package au.homefinder.app.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/** A position in Web Mercator world pixels at some zoom level. */
data class WorldPoint(val x: Double, val y: Double)

data class LonLat(val longitude: Double, val latitude: Double)

/** Slippy-map tile coordinates. */
data class TileId(val zoom: Int, val x: Int, val y: Int) {
    fun url(template: String): String =
        template.replace("{z}", zoom.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
}

object WebMercator {
    const val TILE_SIZE: Double = 256.0
    const val MIN_ZOOM: Double = 2.0
    const val MAX_ZOOM: Double = 19.0
    private const val MAX_LATITUDE: Double = 85.05112878
    private const val HALF_TURN_DEGREES: Double = 180.0
    private const val FULL_TURN_DEGREES: Double = 360.0

    fun worldSize(zoom: Double): Double = TILE_SIZE * 2.0.pow(zoom)

    fun toWorld(position: LonLat, zoom: Double): WorldPoint {
        val size = worldSize(zoom)
        val sinLat = sin(Math.toRadians(position.latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)))
        return WorldPoint(
            x = (position.longitude + HALF_TURN_DEGREES) / FULL_TURN_DEGREES * size,
            y = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * size,
        )
    }

    fun toLonLat(point: WorldPoint, zoom: Double): LonLat {
        val size = worldSize(zoom)
        val longitude = point.x / size * FULL_TURN_DEGREES - HALF_TURN_DEGREES
        val latitude = Math.toDegrees(atan(kotlin.math.sinh(PI * (1 - 2 * point.y / size))))
        return LonLat(longitude, latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))
    }

    fun tileAt(point: WorldPoint): Pair<Int, Int> = floor(point.x / TILE_SIZE).toInt() to floor(point.y / TILE_SIZE).toInt()
}

/** A tile placed on screen: top-left offset and edge length in pixels. */
data class PlacedTile(val tile: TileId, val left: Float, val top: Float, val size: Float)

/** The map view: a centre and a (fractional) zoom. All operations are pure so they can be tested without a window. */
data class RasterViewport(val centre: LonLat, val zoom: Double) {
    constructor(latitude: Double, longitude: Double, zoom: Double) : this(LonLat(longitude, latitude), zoom)

    fun pannedBy(dxPixels: Double, dyPixels: Double): RasterViewport {
        val world = WebMercator.toWorld(centre, zoom)
        return copy(centre = WebMercator.toLonLat(WorldPoint(world.x - dxPixels, world.y - dyPixels), zoom))
    }

    /** Zooms by [delta] keeping the point under the cursor fixed; offsets are from the viewport centre in pixels. */
    fun zoomedAt(delta: Double, cursorDx: Double, cursorDy: Double): RasterViewport {
        val newZoom = (zoom + delta).coerceIn(WebMercator.MIN_ZOOM, WebMercator.MAX_ZOOM)
        val centreWorld = WebMercator.toWorld(centre, zoom)
        val underCursor = WebMercator.toLonLat(WorldPoint(centreWorld.x + cursorDx, centreWorld.y + cursorDy), zoom)
        val cursorWorld = WebMercator.toWorld(underCursor, newZoom)
        return RasterViewport(
            WebMercator.toLonLat(WorldPoint(cursorWorld.x - cursorDx, cursorWorld.y - cursorDy), newZoom),
            newZoom,
        )
    }

    /** The tiles covering a [widthPx] by [heightPx] viewport. Tiles are drawn at the integer zoom below [zoom]. */
    fun visibleTiles(widthPx: Double, heightPx: Double): List<PlacedTile> {
        if (widthPx <= 0 || heightPx <= 0) return emptyList()
        val tileZoom = floor(zoom).toInt()
        val scale = 2.0.pow(zoom - tileZoom)
        val centreWorld = WebMercator.toWorld(centre, tileZoom.toDouble())
        val left = centreWorld.x - widthPx / 2 / scale
        val top = centreWorld.y - heightPx / 2 / scale
        val tilesPerSide = 1 shl tileZoom
        val firstX = floor(left / WebMercator.TILE_SIZE).toInt()
        val lastX = floor((left + widthPx / scale) / WebMercator.TILE_SIZE).toInt()
        val firstY = floor(top / WebMercator.TILE_SIZE).toInt().coerceAtLeast(0)
        val lastY = floor((top + heightPx / scale) / WebMercator.TILE_SIZE).toInt().coerceAtMost(tilesPerSide - 1)
        val edge = (WebMercator.TILE_SIZE * scale).toFloat()
        return buildList {
            for (tileY in firstY..lastY) for (tileX in firstX..lastX) {
                add(
                    PlacedTile(
                        TileId(tileZoom, Math.floorMod(tileX, tilesPerSide), tileY),
                        ((tileX * WebMercator.TILE_SIZE - left) * scale).toFloat(),
                        ((tileY * WebMercator.TILE_SIZE - top) * scale).toFloat(),
                        edge,
                    ),
                )
            }
        }
    }
}
