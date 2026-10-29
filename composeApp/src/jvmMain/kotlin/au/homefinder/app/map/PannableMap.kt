package au.homefinder.app.map

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SCROLL_ZOOM_STEP = 0.25
private const val BUTTON_ZOOM_STEP = 1.0
private const val ATTRIBUTION_ALPHA = 0.8f
private const val ATTRIBUTION_TEXT_SIZE_SP = 11

/**
 * The interaction shared by the desktop fallback renderers: drag to pan, scroll wheel to zoom around the cursor, zoom
 * buttons, an attribution line, and taps reported as coordinates. The renderer draws the tiles in [content] from the current viewport.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PannableMap(
    initialCamera: MapCamera,
    attribution: String,
    modifier: Modifier = Modifier,
    onTap: (MapTap) -> Unit = {},
    content: @Composable BoxScope.(viewport: RasterViewport, size: IntSize) -> Unit,
) {
    var viewport by remember {
        mutableStateOf(RasterViewport(initialCamera.latitude, initialCamera.longitude, initialCamera.zoom))
    }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier
            .onSizeChanged { size = it }
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val position = viewport.lonLatAt(tap.x - size.width / 2.0, tap.y - size.height / 2.0)
                    onTap(MapTap(position.latitude, position.longitude, viewport.zoom))
                }
            }
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
        content(viewport, size)
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
                attribution,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                fontSize = ATTRIBUTION_TEXT_SIZE_SP.sp,
            )
        }
    }
}
