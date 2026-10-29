package au.homefinder.app.map.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StreetLabelGeometryTest {
    private fun anchor(points: FloatArray, width: Float = 100f, deviation: Float = 5f) =
        StreetLabelGeometry.anchorAlongLine(points, width, deviation)

    @Test
    fun `a horizontal road gives an unrotated label at its middle`() {
        val a = assertNotNull(anchor(floatArrayOf(0f, 50f, 400f, 50f)))
        assertEquals(200f, a.x, 0.01f)
        assertEquals(50f, a.y, 0.01f)
        assertEquals(0f, a.angleDegrees, 0.01f)
    }

    @Test
    fun `a road running down and right is rotated clockwise`() {
        val a = assertNotNull(anchor(floatArrayOf(0f, 0f, 300f, 300f)))
        assertEquals(45f, a.angleDegrees, 0.01f)
    }

    @Test
    fun `text is never upside down`() {
        val leftward = assertNotNull(anchor(floatArrayOf(400f, 50f, 0f, 50f)))
        assertEquals(0f, leftward.angleDegrees, 0.01f)
        val upLeft = assertNotNull(anchor(floatArrayOf(300f, 300f, 0f, 0f)))
        assertEquals(45f, upLeft.angleDegrees, 0.01f)
    }

    @Test
    fun `a vertical road reads bottom to top`() {
        val down = assertNotNull(anchor(floatArrayOf(10f, 0f, 10f, 400f)))
        assertEquals(90f, down.angleDegrees, 0.01f)
        val up = assertNotNull(anchor(floatArrayOf(10f, 400f, 10f, 0f)))
        assertEquals(90f, up.angleDegrees, 0.01f)
    }

    @Test
    fun `a road shorter than the text gets no label`() {
        assertNull(anchor(floatArrayOf(0f, 0f, 80f, 0f)))
        assertNull(anchor(floatArrayOf(0f, 0f, 105f, 0f)))
    }

    @Test
    fun `a bend under the text is rejected but a gentle one is allowed`() {
        assertNull(anchor(floatArrayOf(0f, 0f, 150f, 0f, 150f, 150f, 300f, 150f), width = 200f))
        assertNotNull(anchor(floatArrayOf(0f, 0f, 200f, 3f, 400f, 0f)))
    }

    @Test
    fun `a single point or empty line has no anchor`() {
        assertNull(anchor(floatArrayOf(5f, 5f)))
        assertNull(anchor(floatArrayOf()))
    }

    @Test
    fun `the label is centred by arc length on a multi-segment line`() {
        val a = assertNotNull(anchor(floatArrayOf(0f, 0f, 100f, 0f, 200f, 0f, 500f, 0f), width = 100f))
        assertEquals(250f, a.x, 0.01f)
    }
}
