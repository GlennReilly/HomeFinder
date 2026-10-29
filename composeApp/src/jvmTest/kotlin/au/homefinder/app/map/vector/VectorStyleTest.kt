package au.homefinder.app.map.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VectorStyleTest {
    @Test
    fun `important roads are drawn wider and later`() {
        val motorway = assertNotNull(VectorStyle.road("motorway", 14.0))
        val minor = assertNotNull(VectorStyle.road("minor", 14.0))
        assertTrue(VectorStyle.roadWidthPx(motorway, 14.0) > VectorStyle.roadWidthPx(minor, 14.0))
        assertTrue(VectorStyle.roadDrawOrder.indexOf("motorway") > VectorStyle.roadDrawOrder.indexOf("minor"))
    }

    @Test
    fun `minor roads appear only from zoom 12`() {
        assertNull(VectorStyle.road("minor", 11.5))
        assertNotNull(VectorStyle.road("minor", 12.0))
        assertNotNull(VectorStyle.road("motorway", 5.0))
    }

    @Test
    fun `road width grows with zoom but stays within limits`() {
        val road = assertNotNull(VectorStyle.road("primary", 14.0))
        assertTrue(VectorStyle.roadWidthPx(road, 17.0) > VectorStyle.roadWidthPx(road, 14.0))
        assertEquals(road.widthAt14 * 3f, VectorStyle.roadWidthPx(road, 30.0), 1e-4f)
        assertTrue(VectorStyle.roadWidthPx(road, 2.0) >= 0.8f)
    }

    @Test
    fun `casings are wider than their roads`() {
        val road = assertNotNull(VectorStyle.road("secondary", 14.0))
        assertTrue(VectorStyle.casingWidthPx(road, 14.0) > VectorStyle.roadWidthPx(road, 14.0))
    }

    @Test
    fun `unknown road classes are not drawn`() {
        assertNull(VectorStyle.road("teleporter", 14.0))
        assertNull(VectorStyle.road(null, 14.0))
    }

    @Test
    fun `buildings appear only at close zoom and water always`() {
        assertNull(VectorStyle.fill("building", null, 12.0))
        assertNotNull(VectorStyle.fill("building", null, 14.0))
        assertNotNull(VectorStyle.fill("water", "ocean", 2.0))
    }

    @Test
    fun `place labels use class specific zoom thresholds`() {
        assertEquals(4.0, assertNotNull(VectorStyle.labelStyle("place", "city")).minZoom)
        assertEquals(11.0, assertNotNull(VectorStyle.labelStyle("place", "suburb")).minZoom)
        assertNull(VectorStyle.labelStyle("place", "continent"))
        assertNotNull(VectorStyle.labelStyle("poi", "shop"))
        assertNull(VectorStyle.labelStyle("poi", "bus"))
    }

    @Test
    fun `every drawn layer is either a polygon or a line layer`() {
        val known = VectorStyle.polygonLayers + VectorStyle.lineLayers
        assertTrue(VectorStyle.steps.all { it.layer in known })
    }

    @Test
    fun `street names appear for bigger roads first`() {
        assertEquals(11.0, assertNotNull(VectorStyle.streetStyle("motorway")).minZoom)
        assertEquals(15.0, assertNotNull(VectorStyle.streetStyle("minor")).minZoom)
        assertTrue(assertNotNull(VectorStyle.streetStyle("primary")).priority < assertNotNull(VectorStyle.streetStyle("minor")).priority)
        assertNull(VectorStyle.streetStyle("rail"))
    }

    @Test
    fun `house numbers read from their own property and appear last`() {
        assertEquals("housenumber", VectorStyle.labelTextKey("housenumber"))
        assertEquals("name", VectorStyle.labelTextKey("poi"))
        assertEquals(17.0, assertNotNull(VectorStyle.labelStyle("housenumber", null)).minZoom)
        assertTrue(assertNotNull(VectorStyle.labelStyle("housenumber", null)).priority > assertNotNull(VectorStyle.labelStyle("poi", "shop")).priority)
    }
}
