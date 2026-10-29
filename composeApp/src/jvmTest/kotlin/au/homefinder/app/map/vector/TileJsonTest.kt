package au.homefinder.app.map.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TileJsonTest {
    @Test
    fun `reads the tile template and max zoom`() {
        val info = TileJson.parse(
            """{"tilejson":"3.0.0","tiles":["https://t.example/planet/20261025_pt/{z}/{x}/{y}.pbf"],"minzoom":0,"maxzoom":14}""",
        )
        assertEquals("https://t.example/planet/20261025_pt/{z}/{x}/{y}.pbf", info.template)
        assertEquals(14, info.maxZoom)
    }

    @Test
    fun `max zoom defaults when absent`() {
        assertEquals(TileJson.DEFAULT_MAX_ZOOM, TileJson.parse("""{"tiles":["https://t/{z}/{x}/{y}"]}""").maxZoom)
    }

    @Test
    fun `a document without tiles is rejected`() {
        assertFailsWith<NoSuchElementException> { TileJson.parse("""{"maxzoom":14}""") }
    }
}
