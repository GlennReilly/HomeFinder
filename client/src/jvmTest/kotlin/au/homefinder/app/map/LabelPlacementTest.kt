package au.homefinder.app.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelPlacementTest {
    private fun ring(vararg points: Pair<Double, Double>) = points.map { LonLat(it.first, it.second) }
    private fun square(x0: Double, y0: Double, x1: Double, y1: Double) = ring(x0 to y0, x1 to y0, x1 to y1, x0 to y1, x0 to y0)

    private val suburb = OutlinePiece(listOf(square(0.0, 0.0, 10.0, 10.0)))

    @Test
    fun `a piece contains the points inside its ring and not those outside`() {
        assertTrue(suburb.contains(LonLat(5.0, 5.0)))
        assertFalse(suburb.contains(LonLat(11.0, 5.0)))
        assertFalse(suburb.contains(LonLat(5.0, -1.0)))
    }

    @Test
    fun `a hole is outside the piece`() {
        val ringed = OutlinePiece(listOf(square(0.0, 0.0, 10.0, 10.0), square(4.0, 4.0, 6.0, 6.0)))
        assertTrue(ringed.contains(LonLat(2.0, 2.0)))
        assertFalse(ringed.contains(LonLat(5.0, 5.0)))
    }

    @Test
    fun `the usual label point is used whenever it is in view`() {
        val preferred = LonLat(3.0, 3.0)
        assertEquals(preferred, LabelPlacement.place(listOf(suburb), Box(-5.0, -5.0, 20.0, 20.0), preferred))
        assertEquals(preferred, LabelPlacement.place(listOf(suburb), Box(2.0, 2.0, 4.0, 4.0), preferred))
    }

    @Test
    fun `once zoomed past it the label moves to the visible part of the suburb near the middle of the view`() {
        val view = Box(6.0, 6.0, 9.0, 9.0)
        val placed = assertNotNull(LabelPlacement.place(listOf(suburb), view, LonLat(1.0, 1.0)))
        assertTrue(view.contains(placed) && suburb.contains(placed), "$placed")
        assertEquals(7.5, placed.longitude, 0.5)
        assertEquals(7.5, placed.latitude, 0.5)
    }

    @Test
    fun `a view that only partly overlaps the suburb gets a label inside the overlap`() {
        val view = Box(8.0, 8.0, 30.0, 30.0)
        val placed = assertNotNull(LabelPlacement.place(listOf(suburb), view, null))
        assertTrue(placed.longitude in 8.0..10.0 && placed.latitude in 8.0..10.0, "$placed")
    }

    @Test
    fun `a suburb that is not in view has no label`() {
        assertNull(LabelPlacement.place(listOf(suburb), Box(20.0, 20.0, 30.0, 30.0), LonLat(1.0, 1.0)))
        assertNull(LabelPlacement.place(emptyList(), Box(0.0, 0.0, 10.0, 10.0), null))
    }

    @Test
    fun `an L-shaped suburb is labelled inside, not in the empty corner of its bounding box`() {
        val l = OutlinePiece(listOf(ring(0.0 to 0.0, 10.0 to 0.0, 10.0 to 2.0, 2.0 to 2.0, 2.0 to 10.0, 0.0 to 10.0, 0.0 to 0.0)))
        val placed = assertNotNull(LabelPlacement.place(listOf(l), Box(0.0, 0.0, 12.0, 12.0), null))
        assertTrue(l.contains(placed), "$placed is inside the L")
        // The middle of the box (5, 5) is the empty corner of the L; the label must not be there.
        assertFalse(placed.longitude > 2.0 && placed.latitude > 2.0, "$placed")
    }

    @Test
    fun `a suburb split into pieces is labelled in the piece that is in view`() {
        val east = OutlinePiece(listOf(square(20.0, 0.0, 30.0, 10.0)))
        val placed = assertNotNull(LabelPlacement.place(listOf(suburb, east), Box(21.0, 1.0, 29.0, 9.0), LonLat(5.0, 5.0)))
        assertTrue(east.contains(placed), "$placed")
    }

    @Test
    fun `a thin sliver falls back to a vertex in view`() {
        val sliver = OutlinePiece(listOf(ring(0.0 to 0.0, 100.0 to 0.0, 100.0 to 0.001, 0.0 to 0.001, 0.0 to 0.0)))
        val view = Box(0.0, -1.0, 50.0, 1.0)
        val placed = assertNotNull(LabelPlacement.place(listOf(sliver), view, null))
        assertTrue(view.contains(placed))
    }

    @Test
    fun `boxes intersect only where they overlap`() {
        assertEquals(Box(2.0, 2.0, 4.0, 4.0), Box(0.0, 0.0, 4.0, 4.0).intersect(Box(2.0, 2.0, 8.0, 8.0)))
        assertNull(Box(0.0, 0.0, 1.0, 1.0).intersect(Box(2.0, 2.0, 3.0, 3.0)))
        assertEquals(LonLat(1.0, 2.0), Box(0.0, 0.0, 2.0, 4.0).center)
    }
}
