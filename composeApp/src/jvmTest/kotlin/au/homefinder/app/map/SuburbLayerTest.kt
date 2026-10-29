package au.homefinder.app.map

import au.homefinder.app.api.ApiConfig
import au.homefinder.app.mapConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuburbLayerTest {
    private val config = SuburbTileConfig("http://10.0.2.2:8080/", "secret-key-123456")

    @Test
    fun `the tile url template points at the suburb tiles`() {
        assertEquals("http://10.0.2.2:8080/tiles/suburbs/{z}/{x}/{y}.pbf", config.tilesUrl(keyInQuery = false))
    }

    @Test
    fun `a client that cannot set headers gets the key as a query parameter`() {
        assertEquals("http://10.0.2.2:8080/tiles/suburbs/{z}/{x}/{y}.pbf?key=secret-key-123456", config.tilesUrl(keyInQuery = true))
    }

    @Test
    fun `the base url may or may not end with a slash`() {
        assertEquals(config.tilesUrl(false), SuburbTileConfig("http://10.0.2.2:8080", "k").tilesUrl(false))
    }

    @Test
    fun `the key is never printed`() {
        assertFalse(config.toString().contains("secret-key"))
    }

    @Test
    fun `outline is fully opaque until zoom 12 and faint from zoom 15`() {
        assertEquals(1f, SuburbStyle.outlineOpacity(5.0))
        assertEquals(1f, SuburbStyle.outlineOpacity(12.0))
        assertEquals(SuburbStyle.OUTLINE_FADED_OPACITY, SuburbStyle.outlineOpacity(15.0))
        assertEquals(SuburbStyle.OUTLINE_FADED_OPACITY, SuburbStyle.outlineOpacity(18.0))
    }

    @Test
    fun `outline opacity fades steadily between zoom 12 and 15`() {
        val steps = listOf(12.0, 12.5, 13.0, 13.5, 14.0, 14.5, 15.0).map { SuburbStyle.outlineOpacity(it) }
        assertTrue(steps.zipWithNext().all { (a, b) -> a > b }, "$steps")
        assertEquals((1f + SuburbStyle.OUTLINE_FADED_OPACITY) / 2, SuburbStyle.outlineOpacity(13.5), 1e-6f)
    }

    @Test
    fun `names show from zoom 5 until the map reaches street level`() {
        assertFalse(SuburbStyle.labelsVisible(4.9))
        assertTrue(SuburbStyle.labelsVisible(5.0))
        assertTrue(SuburbStyle.labelsVisible(13.9))
        assertFalse(SuburbStyle.labelsVisible(14.0))
        assertFalse(SuburbStyle.outlineVisible(4.9))
        assertTrue(SuburbStyle.outlineVisible(5.0))
    }

    @Test
    fun `bigger suburbs are named first`() {
        assertTrue(SuburbStyle.labelPriority(80.0) < SuburbStyle.labelPriority(2.5))
        assertTrue(SuburbStyle.labelPriority(2.5) < SuburbStyle.labelPriority(0.4))
    }

    @Test
    fun `the map gets suburb layers only when the API and its key are known`() {
        assertNull(mapConfig(null).suburbs)
        assertNull(mapConfig(ApiConfig("http://host:8080", "")).suburbs)
        assertNull(mapConfig(ApiConfig("http://host:8080", "   ")).suburbs)
        val suburbs = assertNotNull(mapConfig(ApiConfig("http://host:8080", "abc")).suburbs)
        assertEquals(SuburbTileConfig("http://host:8080", "abc"), suburbs)
        assertEquals(MapConfig.Pilot.initialCamera, mapConfig(ApiConfig("http://host:8080", "abc")).initialCamera)
    }

    @Test
    fun `names use a font OpenFreeMap serves, not the MapLibre default`() {
        assertTrue(SuburbStyle.LABEL_FONT_STACK.isNotEmpty())
        assertTrue(SuburbStyle.LABEL_FONT_STACK.all { it.startsWith("Noto Sans") })
        assertFalse(SuburbStyle.LABEL_FONT_STACK.any { it.contains("Open Sans") })
    }
}
