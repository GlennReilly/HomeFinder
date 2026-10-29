package au.homefinder.app.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import au.homefinder.app.map.suburb.SuburbOverlay
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val MAX_CACHED_TILES = 512
private val MAP_BACKGROUND = Color(0xFFE8E4DC)

/** Desktop fallback: a raster-tile map drawn on a Compose canvas, so it needs no GPU interop (ADR 0009). */
val RasterTileRenderer: MapRenderer = MapRenderer { config, modifier ->
    PannableMap(config.initialCamera, OsmTileFetcher.ATTRIBUTION, modifier) { viewport, size ->
        RasterTileLayer(viewport, size)
        config.suburbs?.let { SuburbOverlay(it, viewport, size) }
    }
}

@Composable
private fun BoxScope.RasterTileLayer(viewport: RasterViewport, size: IntSize) {
    val scope = rememberCoroutineScope()
    val fetcher = remember { OsmTileFetcher() }
    DisposableEffect(fetcher) { onDispose { fetcher.close() } }
    val store = remember { TileStore<ImageBitmap>(scope, MAX_CACHED_TILES, fetcher::fetch) }
    val placed = remember(viewport, size) { viewport.visibleTiles(size.width.toDouble(), size.height.toDouble()) }
    LaunchedEffect(placed) { placed.forEach { store.request(it.tile) } }

    Canvas(Modifier.fillMaxSize()) {
        drawRect(MAP_BACKGROUND)
        placed.forEach { tile ->
            store.get(tile.tile)?.let { image ->
                val edge = ceil(tile.size).toInt()
                drawImage(
                    image,
                    dstOffset = IntOffset(tile.left.roundToInt(), tile.top.roundToInt()),
                    dstSize = IntSize(edge, edge),
                )
            }
        }
    }
}
