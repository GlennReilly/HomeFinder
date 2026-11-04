package au.homefinder.drainage

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class DrainageRunnerTest {
    @TempDir
    lateinit var dir: Path

    private val pixelDeg = 0.001
    private val westDeg = 151.0
    private val northDeg = -33.0
    private val columns = 9
    private val rows = 12

    /** About 20 pixels at this latitude: only the valley floor drains that much ground, not the walls beside it. */
    private val HILLSIDES_ARE_NOT_STREAMS_HA = 20.0

    /** A V valley down column 4 that falls 0.1 m a row to the south, between walls that rise 2 m a column. */
    private fun valleyTile() {
        val heights = FloatArray(columns * rows) { cell ->
            val row = cell / columns
            val column = cell % columns
            10f + kotlin.math.abs(column - 4) * 2f + (rows - 1 - row) * 0.1f
        }
        GeoTiffFixtures.write(dir.resolve("valley.tif"), columns, rows, westDeg, northDeg, pixelDeg, pixelDeg, heights)
    }

    private fun lotAt(id: String, row: Int, column: Int) = LotPoint(id, westDeg + (column + 0.5) * pixelDeg, northDeg - (row + 0.5) * pixelDeg)

    private fun tiles(): DemTiles = assertIs<DemOpenResult.Opened>(DemTiles.open(dir)).tiles

    @Test
    fun `lots on the valley floor sit at drainage level and lots up the wall stand above it`() {
        valleyTile()
        val lots = listOf(lotAt("floor", 6, 4), lotAt("wall", 6, 2), lotAt("crest", 6, 1))
        val results = DrainageRunner.run(tiles(), lots, streamCatchmentHa = HILLSIDES_ARE_NOT_STREAMS_HA).associateBy { it.lotId }

        assertEquals(0.0, results.getValue("floor").heightAboveDrainageM, 0.01)
        val wall = results.getValue("wall").heightAboveDrainageM
        val crest = results.getValue("crest").heightAboveDrainageM
        assertTrue(wall in 3.9..4.6, "wall was $wall")
        assertTrue(crest in 5.9..6.6 && crest > wall, "crest was $crest")
        assertTrue(results.getValue("wall").streamCatchmentHa > 0.0)
    }

    @Test
    fun `a lot outside the DEM or with no data is left out`() {
        valleyTile()
        val lots = listOf(lotAt("inside", 6, 4), LotPoint("far", 160.0, -20.0), LotPoint("edge", westDeg - 0.00001, northDeg - 0.003))
        val ids = DrainageRunner.run(tiles(), lots, 2.5).map { it.lotId }
        assertEquals(listOf("inside"), ids)
    }

    @Test
    fun `results are sorted by lot and logged per block`() {
        valleyTile()
        val log = mutableListOf<String>()
        val results = DrainageRunner.run(tiles(), listOf(lotAt("b", 6, 4), lotAt("a", 7, 4)), 2.5) { log += it }
        assertEquals(listOf("a", "b"), results.map { it.lotId })
        assertTrue(log.single().startsWith("Block 1 of 1"), log.toString())
    }

    @Test
    fun `an empty or cut short tile is reported once however many blocks reach it`() {
        // Block edges fall on multiples of BLOCK_DEG (0.08): 151.04 is one, so these two lots are in different blocks that both reach both tiles.
        val west = 151.035
        val heights = FloatArray(columns * rows) { cell -> 10f + kotlin.math.abs(cell % columns - 4) * 2f + (rows - 1 - cell / columns) * 0.1f }
        GeoTiffFixtures.write(dir.resolve("valley.tif"), columns, rows, west, northDeg, pixelDeg, pixelDeg, heights)
        GeoTiffFixtures.write(dir.resolve("cut.tif"), columns, rows, west - columns * pixelDeg, northDeg, pixelDeg, pixelDeg, heights, keepPixelBytes = 8)
        val lots = listOf(LotPoint("a", 151.0399, northDeg - 6.5 * pixelDeg), LotPoint("b", 151.0401, northDeg - 6.5 * pixelDeg))
        val log = mutableListOf<String>()
        DrainageRunner.run(tiles(), lots, HILLSIDES_ARE_NOT_STREAMS_HA) { log += it }
        assertEquals(2, log.count { it.startsWith("Block") }, "the lots fall in two blocks")
        assertEquals(1, log.count { "cut.tif" in it && it.startsWith("Warning") }, log.toString())
    }

    @Test
    fun `lots far apart are worked in separate blocks`() {
        valleyTile()
        val log = mutableListOf<String>()
        DrainageRunner.run(tiles(), listOf(lotAt("here", 6, 4), LotPoint("elsewhere", 152.0, -33.5)), 2.5) { log += it }
        assertEquals(2, log.size)
        assertTrue(log.any { "no DEM covers" in it }, log.toString())
    }
}
