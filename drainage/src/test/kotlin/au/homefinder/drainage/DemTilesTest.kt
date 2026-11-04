package au.homefinder.drainage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class DemTilesTest {
    @TempDir
    lateinit var dir: Path

    private val pixelDeg = 0.001
    private val westDeg = 151.0
    private val northDeg = -33.0
    private val tileColumns = 4
    private val tileRows = 3

    /** Heights are `base + row * 10 + column`, so every pixel says where it came from. */
    private fun tile(name: String, west: Double, base: Float, pixelX: Double = pixelDeg, keepPixelBytes: Int = Int.MAX_VALUE) {
        val heights = FloatArray(tileColumns * tileRows) { base + (it / tileColumns) * 10 + it % tileColumns }
        GeoTiffFixtures.write(dir.resolve(name), tileColumns, tileRows, west, northDeg, pixelX, pixelDeg, heights, keepPixelBytes)
    }

    private fun opened(): DemTiles = when (val result = DemTiles.open(dir)) {
        is DemOpenResult.Opened -> result.tiles
        is DemOpenResult.Failed -> throw AssertionError("Expected the tiles to open but: ${result.reason}")
    }

    @Test
    fun `a window across two neighbouring tiles joins their pixels in place`() {
        tile("a.tif", westDeg, 0f)
        tile("b.tif", westDeg + 4 * pixelDeg, 100f)
        val tiles = opened()
        assertEquals(2, tiles.tileCount)

        // Mid-pixel edges: columns 2 to 5 (two from tile a, two from tile b) and all three rows.
        val window = tiles.window(151.0025, -33.0025, 151.0055, -33.0005)
        val grid = assertNotNull(window.grid)
        assertEquals(4, grid.columns)
        assertEquals(3, grid.rows)
        assertEquals(151.002, grid.westDeg, 1e-9)
        assertEquals(-33.0, grid.northDeg, 1e-9)
        val expected = (0 until 3).flatMap { row -> listOf(row * 10f + 2, row * 10f + 3, 100f + row * 10 + 0, 100f + row * 10 + 1) }
        assertEquals(expected, grid.heightsM.toList())
        assertTrue(window.unreadable.isEmpty())
    }

    @Test
    fun `parts of a window no tile covers are no data`() {
        tile("a.tif", westDeg, 0f)
        val grid = assertNotNull(opened().window(151.0025, -33.0025, 151.0065, -33.0005).grid)
        assertEquals(5, grid.columns)
        // Columns 2 and 3 are inside the only tile; columns 4 to 6 lie past it.
        assertEquals(listOf(false, false, true, true, true), (0 until 5).map { grid.heightsM[it].isNaN() })
        assertEquals(2f, grid.heightsM[0])
    }

    @Test
    fun `a window no tile reaches has no grid`() {
        tile("a.tif", westDeg, 0f)
        assertNull(opened().window(152.0, -34.0, 152.1, -33.9).grid)
    }

    @Test
    fun `a truncated tile is reported and its pixels count as no data`() {
        tile("a.tif", westDeg, 0f)
        tile("b.tif", westDeg + 4 * pixelDeg, 100f, keepPixelBytes = 8)
        val window = opened().window(151.0025, -33.0025, 151.0055, -33.0005)
        assertEquals(listOf("b.tif"), window.unreadable.map { it.fileName.toString() })
        val grid = assertNotNull(window.grid)
        assertEquals(2f, grid.heightsM[0])
        assertTrue(grid.heightsM[2].isNaN() && grid.heightsM[3].isNaN())
    }

    @Test
    fun `an empty directory is refused with a plain message`() {
        val failed = assertIs<DemOpenResult.Failed>(DemTiles.open(dir))
        assertTrue("Missing GeoTIFF" in failed.reason, failed.reason)
    }

    @Test
    fun `a file that is not a tiff is refused and named`() {
        Files.writeString(dir.resolve("x.tif"), "not a raster")
        val failed = assertIs<DemOpenResult.Failed>(DemTiles.open(dir))
        assertTrue("x.tif" in failed.reason, failed.reason)
    }

    @Test
    fun `tiles with different pixel sizes are refused`() {
        tile("a.tif", westDeg, 0f)
        tile("b.tif", westDeg + 4 * pixelDeg, 100f, pixelX = pixelDeg * 2)
        val failed = assertIs<DemOpenResult.Failed>(DemTiles.open(dir))
        assertTrue("pixel size" in failed.reason, failed.reason)
    }

    @Test
    fun `something that is not a directory is refused`() {
        assertIs<DemOpenResult.Failed>(DemTiles.open(dir.resolve("missing")))
    }
}
