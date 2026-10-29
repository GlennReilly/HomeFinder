package au.homefinder.app.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val SCROLL_ZOOM_STEP = 0.25
private const val BUTTON_ZOOM_STEP = 1.0
private const val ATTRIBUTION_ALPHA = 0.8f
private const val ATTRIBUTION_TEXT_SIZE_SP = 11
private val MAP_BACKGROUND = Color(0xFFE8E4DC)

/** Desktop fallback: a raster-tile map drawn on a Compose canvas, so it needs no GPU interop (ADR 0009). */
val RasterTileRenderer: MapRenderer = MapRenderer { config, modifier -> RasterTileMap(config.initialCamera, modifier) }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RasterTileMap(initialCamera: MapCamera, modifier: Modifier = Modifier) {
    var viewport by remember {
        mutableStateOf(RasterViewport(initialCamera.latitude, initialCamera.longitude, initialCamera.zoom))
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val fetcher = remember { OsmTileFetcher() }
    DisposableEffect(fetcher) { onDispose { fetcher.close() } }
    val store = remember { TileStore(scope, fetcher::fetch) }
    val placed = remember(viewport, size) { viewport.visibleTiles(size.width.toDouble(), size.height.toDouble()) }
    LaunchedEffect(placed) { placed.forEach { store.request(it.tile) } }

    Box(
        modifier
            .onSizeChanged { size = it }
            .clipToBounds()
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    viewport = viewport.pannedBy(drag.x.toDouble(), drag.y.toDouble())
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.first()
                viewport = viewport.zoomedAt(
                    delta = -change.scrollDelta.y * SCROLL_ZOOM_STEP,
                    cursorDx = change.position.x - size.width / 2.0,
                    cursorDy = change.position.y - size.height / 2.0,
                )
            },
    ) {
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
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Button(
                onClick = { viewport = viewport.zoomedAt(BUTTON_ZOOM_STEP, 0.0, 0.0) },
                modifier = Modifier.semantics { contentDescription = "Zoom in" },
            ) { Text("+") }
            Button(
                onClick = { viewport = viewport.zoomedAt(-BUTTON_ZOOM_STEP, 0.0, 0.0) },
                modifier = Modifier.padding(top = 4.dp).semantics { contentDescription = "Zoom out" },
            ) { Text("−") }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = ATTRIBUTION_ALPHA),
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Text(
                OsmTileFetcher.ATTRIBUTION,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                fontSize = ATTRIBUTION_TEXT_SIZE_SP.sp,
            )
        }
    }
}
