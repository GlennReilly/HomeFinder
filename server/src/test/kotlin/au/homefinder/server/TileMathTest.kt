package au.homefinder.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TileMathTest {
    @Test
    fun `tiles per side doubles with each zoom level`() {
        assertEquals(1L, TileMath.tilesPerSide(0))
        assertEquals(1024L, TileMath.tilesPerSide(10))
    }

    @Test
    fun `coordinates must lie inside the grid`() {
        assertTrue(TileMath.isValid(10, 943, 611))
        assertTrue(TileMath.isValid(0, 0, 0))
        assertTrue(TileMath.isValid(10, 1023, 1023))
        assertFalse(TileMath.isValid(10, 1024, 0))
        assertFalse(TileMath.isValid(10, 0, 1024))
        assertFalse(TileMath.isValid(10, -1, 0))
        assertFalse(TileMath.isValid(-1, 0, 0))
        assertFalse(TileMath.isValid(31, 0, 0))
        assertFalse(TileMath.isValid(0, 1, 0))
    }
}
