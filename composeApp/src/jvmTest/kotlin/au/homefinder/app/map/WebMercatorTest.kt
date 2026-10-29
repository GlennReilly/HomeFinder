package au.homefinder.app.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebMercatorTest {
    private val newcastle = LonLat(longitude = 151.78, latitude = -32.93)

    @Test
    fun `Newcastle at zoom 10 is world pixel 241594 156491 in tile 943 611`() {
        val world = WebMercator.toWorld(newcastle, 10.0)
        assertEquals(241594.823, world.x, 0.01)
        assertEquals(156491.724, world.y, 0.01)
        assertEquals(943 to 611, WebMercator.tileAt(world))
    }

    @Test
    fun `the origin sits in the middle of the zoom 0 world`() {
        val world = WebMercator.toWorld(LonLat(0.0, 0.0), 0.0)
        assertEquals(128.0, world.x, 1e-9)
        assertEquals(128.0, world.y, 1e-9)
    }

    @Test
    fun `world pixels convert back to the same position`() {
        val back = WebMercator.toLonLat(WebMercator.toWorld(newcastle, 13.5), 13.5)
        assertEquals(newcastle.longitude, back.longitude, 1e-9)
        assertEquals(newcastle.latitude, back.latitude, 1e-9)
    }

    @Test
    fun `tile url fills the template`() {
        assertEquals("https://t.example/10/943/611.png", TileId(10, 943, 611).url("https://t.example/{z}/{x}/{y}.png"))
    }

    @Test
    fun `panning right moves the centre west`() {
        val start = RasterViewport(newcastle, 10.0)
        assertTrue(start.pannedBy(100.0, 0.0).centre.longitude < start.centre.longitude)
        assertTrue(start.pannedBy(0.0, 100.0).centre.latitude > start.centre.latitude)
    }

    @Test
    fun `zooming keeps the point under the cursor fixed`() {
        val start = RasterViewport(newcastle, 10.0)
        val cursorDx = 100.0
        val cursorDy = 50.0
        fun lonLatUnderCursor(view: RasterViewport): LonLat {
            val centre = WebMercator.toWorld(view.centre, view.zoom)
            return WebMercator.toLonLat(WorldPoint(centre.x + cursorDx, centre.y + cursorDy), view.zoom)
        }
        val zoomed = start.zoomedAt(1.0, cursorDx, cursorDy)
        assertEquals(11.0, zoomed.zoom, 1e-12)
        assertEquals(lonLatUnderCursor(start).longitude, lonLatUnderCursor(zoomed).longitude, 1e-9)
        assertEquals(lonLatUnderCursor(start).latitude, lonLatUnderCursor(zoomed).latitude, 1e-9)
    }

    @Test
    fun `zoom is clamped to the supported range`() {
        val view = RasterViewport(newcastle, 10.0)
        assertEquals(WebMercator.MAX_ZOOM, view.zoomedAt(100.0, 0.0, 0.0).zoom)
        assertEquals(WebMercator.MIN_ZOOM, view.zoomedAt(-100.0, 0.0, 0.0).zoom)
    }

    @Test
    fun `visible tiles cover the viewport and include the centre tile`() {
        val width = 800.0
        val height = 600.0
        val tiles = RasterViewport(newcastle, 10.0).visibleTiles(width, height)
        val centreTile = tiles.single { it.tile == TileId(10, 943, 611) }
        assertTrue(centreTile.left <= width / 2 && width / 2 <= centreTile.left + centreTile.size)
        assertTrue(centreTile.top <= height / 2 && height / 2 <= centreTile.top + centreTile.size)
        assertTrue(tiles.minOf { it.left } <= 0f && tiles.maxOf { it.left + it.size } >= width)
        assertTrue(tiles.minOf { it.top } <= 0f && tiles.maxOf { it.top + it.size } >= height)
    }

    @Test
    fun `fractional zoom draws tiles larger than 256 pixels from the integer zoom below`() {
        val tiles = RasterViewport(newcastle, 10.5).visibleTiles(800.0, 600.0)
        assertTrue(tiles.all { it.tile.zoom == 10 })
        assertEquals(256f * 1.4142135f, tiles.first().size, 0.01f)
    }

    @Test
    fun `an empty viewport has no tiles`() {
        assertTrue(RasterViewport(newcastle, 10.0).visibleTiles(0.0, 0.0).isEmpty())
    }

    @Test
    fun `tiles wrap around the antimeridian`() {
        val tiles = RasterViewport(LonLat(179.9, 0.0), 3.0).visibleTiles(1200.0, 400.0)
        assertTrue(tiles.all { it.tile.x in 0..7 })
        assertTrue(tiles.any { it.tile.x == 0 } && tiles.any { it.tile.x == 7 })
    }

    @Test
    fun `tiles beyond the source max zoom are the max zoom tiles scaled up`() {
        val tiles = RasterViewport(newcastle, 16.0).visibleTiles(800.0, 600.0, maxTileZoom = 14)
        assertTrue(tiles.all { it.tile.zoom == 14 })
        assertEquals(256f * 4f, tiles.first().size, 0.01f)
    }
}
