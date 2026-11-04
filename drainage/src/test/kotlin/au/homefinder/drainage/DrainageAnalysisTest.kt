package au.homefinder.drainage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrainageAnalysisTest {
    private val pixelDeg = 0.0001
    private val northDeg = -33.0
    private val tolerance = 0.002f

    private fun grid(rows: List<List<Float>>): ElevationGrid {
        val columns = rows.first().size
        return ElevationGrid(columns, rows.size, 151.0, northDeg, pixelDeg, pixelDeg, rows.flatten().toFloatArray())
    }

    /** A stream size in hectares that works out to exactly [cells] pixels of this grid (half a pixel under, so rounding up gives [cells]). */
    private fun hectaresFor(cells: Int, grid: ElevationGrid): Double = (cells - 0.5) * grid.cellAreaM2 / 10_000.0

    /** Three columns, six rows: a middle gutter falling 0.1 m a row to the south between 20 m walls. */
    private fun gutter(): ElevationGrid = grid((0 until 6).map { row -> listOf(20f, 5f - row * 0.1f, 20f) })

    @Test
    fun `a gutter's stream pixels are the ones that drain enough ground, and their catchments are counted`() {
        val grid = gutter()
        val result = DrainageAnalysis.analyse(grid, hectaresFor(3, grid))
        val at = { row: Int, column: Int -> row * 3 + column }

        // Rows 5, 4 and 3 of the gutter drain 5, 4 and 3 pixels: all streams, so they sit at their own level.
        for (row in 3..5) assertEquals(0f, result.heightAboveDrainageM[at(row, 1)], tolerance)
        val pixelHa = (grid.cellAreaM2 / 10_000.0).toFloat()
        assertEquals(5 * pixelHa, result.streamCatchmentHa[at(5, 1)], 1e-6f)
        assertEquals(4 * pixelHa, result.streamCatchmentHa[at(4, 1)], 1e-6f)
    }

    @Test
    fun `a pixel above a stream is measured from the stream pixel its water reaches`() {
        val grid = gutter()
        val result = DrainageAnalysis.analyse(grid, hectaresFor(3, grid))
        // Row 2 of the gutter drains only 2 pixels, so it is not a stream: it drains to row 3 (4.7 m) and stands 0.1 m above it.
        assertEquals(0.1f, result.heightAboveDrainageM[2 * 3 + 1], tolerance)
        // It reports the size of the stream it drains to (3 pixels), not its own.
        assertEquals(3 * (grid.cellAreaM2 / 10_000.0).toFloat(), result.streamCatchmentHa[2 * 3 + 1], 1e-6f)
        // Row 1 is 0.2 m above the same stream pixel.
        assertEquals(0.2f, result.heightAboveDrainageM[1 * 3 + 1], tolerance)
    }

    @Test
    fun `a pixel on the grid's edge that drains straight off it is unknown`() {
        val grid = gutter()
        val result = DrainageAnalysis.analyse(grid, hectaresFor(3, grid))
        assertTrue(result.heightAboveDrainageM[0].isNaN(), "a wall pixel on the edge meets no stream")
        assertTrue(result.streamCatchmentHa[0].isNaN())
    }

    @Test
    fun `when every pixel counts as a stream every height above drainage is zero`() {
        val grid = grid(listOf(listOf(9f, 8f, 7f, 6f), listOf(8f, 7f, 6f, 5f), listOf(7f, 6f, 5f, 4f), listOf(6f, 5f, 4f, 3f)))
        val result = DrainageAnalysis.analyse(grid, hectaresFor(1, grid))
        assertTrue(result.heightAboveDrainageM.all { it == 0f })
    }

    @Test
    fun `when no pixel drains enough ground nothing is known`() {
        val grid = gutter()
        val result = DrainageAnalysis.analyse(grid, hectaresFor(1000, grid))
        assertTrue(result.heightAboveDrainageM.all { it.isNaN() })
    }

    @Test
    fun `a hillside is measured from the valley floor it drains to`() {
        // Eleven columns, nine rows: a V valley along column 5 that falls 0.1 m a row to the south.
        val rows = (0 until 9).map { row -> (0 until 11).map { column -> 10f + kotlin.math.abs(column - 5) * 2f + (8 - row) * 0.1f } }
        val grid = grid(rows)
        val result = DrainageAnalysis.analyse(grid, hectaresFor(5, grid))
        val hillside = result.heightAboveDrainageM[4 * 11 + 2]
        val valleyFloor = result.heightAboveDrainageM[4 * 11 + 5]
        assertEquals(0f, valleyFloor, tolerance)
        // The hillside pixel is 6 m above the valley floor at its own row, give or take the 0.4 m the floor falls over the rows it may meet.
        assertTrue(hillside in 5.9f..6.5f, "hillside height above drainage was $hillside")
        val ridge = result.heightAboveDrainageM[4 * 11 + 1]
        assertTrue(ridge > hillside, "pixels further up the slope stand higher above the valley floor")
    }

    @Test
    fun `a pit is filled so the water in it still finds a way out`() {
        // A sloping plane with a one pixel pit in the middle of the gutter's walls: the pit's water must reach the stream.
        val rows = (0 until 7).map { row -> (0 until 5).map { column -> if (column == 2) 5f - row * 0.1f else 20f } }.map { it.toMutableList() }
        rows[3][2] = 1f
        val grid = grid(rows)
        val result = DrainageAnalysis.analyse(grid, hectaresFor(3, grid))
        val pit = result.heightAboveDrainageM[3 * 5 + 2]
        assertFalse(pit.isNaN(), "a pit drains somewhere once filled")
        assertTrue(pit >= 0f)
    }

    @Test
    fun `no data pixels are unknown and act as outlets`() {
        val rows = (0 until 6).map { row -> listOf(20f, 5f - row * 0.1f, 20f) }.map { it.toMutableList() }
        rows[5][1] = Float.NaN
        val grid = grid(rows)
        val result = DrainageAnalysis.analyse(grid, hectaresFor(2, grid))
        assertTrue(result.heightAboveDrainageM[5 * 3 + 1].isNaN(), "the no data pixel itself has no result")
        assertTrue(result.heightAboveDrainageM[4 * 3 + 1] >= 0f, "water from the pixel above drains into the no data pixel and still meets a stream")
    }

    @Test
    fun `heights outside the valid range count as no data`() {
        val rows = (0 until 6).map { row -> listOf(20f, 5f - row * 0.1f, 20f) }.map { it.toMutableList() }
        rows[2][1] = -9999f
        val grid = grid(rows)
        val result = DrainageAnalysis.analyse(grid, hectaresFor(1, grid))
        assertTrue(result.heightAboveDrainageM[2 * 3 + 1].isNaN())
        assertEquals(0f, result.heightAboveDrainageM[3 * 3 + 1], tolerance)
    }

    @Test
    fun `the stream size must be positive`() {
        assertFailsWith<IllegalArgumentException> { DrainageAnalysis.analyse(gutter(), 0.0) }
    }

    @Test
    fun `analysing the same grid twice gives the same answer`() {
        val grid = gutter()
        val first = DrainageAnalysis.analyse(grid, hectaresFor(3, grid))
        val second = DrainageAnalysis.analyse(grid, hectaresFor(3, grid))
        assertTrue(first.heightAboveDrainageM.contentEquals(second.heightAboveDrainageM))
    }

    @Test
    fun `a grid needs as many heights as pixels`() {
        assertFailsWith<IllegalArgumentException> { ElevationGrid(2, 2, 151.0, northDeg, pixelDeg, pixelDeg, FloatArray(3)) }
    }

    @Test
    fun `the grid finds the pixel under a point and nothing outside it`() {
        val grid = ElevationGrid(4, 3, 151.0, northDeg, pixelDeg, pixelDeg, FloatArray(12))
        assertEquals(0, grid.indexAt(151.00005, -33.00005))
        assertEquals(1 * 4 + 2, grid.indexAt(151.00025, -33.00015))
        assertEquals(null, grid.indexAt(150.9999, -33.00005))
        assertEquals(null, grid.indexAt(151.00005, -33.0004))
    }
}
