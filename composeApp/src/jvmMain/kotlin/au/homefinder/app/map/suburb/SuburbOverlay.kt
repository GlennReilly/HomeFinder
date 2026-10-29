package au.homefinder.app.map.suburb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import au.homefinder.app.map.PlacedTile
import au.homefinder.app.map.RasterViewport
import au.homefinder.app.map.SuburbStyle
import au.homefinder.app.map.SuburbTileConfig
import au.homefinder.app.map.TileStore
import au.homefinder.app.map.vector.LabelCandidate
import au.homefinder.app.map.vector.LabelPlacer
import au.homefinder.shared.api.TileContract
import kotlin.math.floor

private const val MAX_CACHED_TILES = 128
private const val LABEL_PADDING_PX = 6f
private const val HALO_OFFSET_PX = 1.5f
private val HALO_OFFSETS = listOf(
    Offset(-HALO_OFFSET_PX, 0f), Offset(HALO_OFFSET_PX, 0f), Offset(0f, -HALO_OFFSET_PX), Offset(0f, HALO_OFFSET_PX),
)

/**
 * Suburb outlines and names from the API's tiles, drawn over either desktop base map. Input passes through to the
 * map underneath. Nothing is drawn below the suburb tiles' minimum zoom.
 */
@Composable
fun BoxScope.SuburbOverlay(config: SuburbTileConfig, selectedSuburbId: String?, viewport: RasterViewport, size: IntSize) {
    val scope = rememberCoroutineScope()
    val http = remember(config) { KtorBytesGet() }
    DisposableEffect(http) { onDispose { http.close() } }
    val store = remember(config) { TileStore<PreparedSuburbTile>(scope, MAX_CACHED_TILES, SuburbTileLoader(config, http)::load) }
    val active = viewport.zoom >= TileContract.SUBURBS_MIN_ZOOM
    val placed = remember(viewport, size, active) {
        if (active) viewport.visibleTiles(size.width.toDouble(), size.height.toDouble(), TileContract.SUBURBS_MAX_ZOOM) else emptyList()
    }
    LaunchedEffect(placed, store) { placed.forEach { store.request(it.tile) } }

    val measurer = rememberTextMeasurer()
    val layouts = remember { HashMap<String, TextLayoutResult>() }

    Canvas(Modifier.fillMaxSize()) {
        val candidates = ArrayList<LabelCandidate<TextLayoutResult>>()
        placed.forEach { tile ->
            val prepared = store.get(tile.tile) ?: return@forEach
            drawHighlight(prepared, tile, selectedSuburbId)
            drawOutlines(prepared, tile, viewport.zoom, selectedSuburbId)
            if (SuburbStyle.labelsVisible(viewport.zoom)) collectLabels(prepared, tile, measurer, layouts, candidates)
        }
        LabelPlacer.place(candidates, this.size.width, this.size.height, LABEL_PADDING_PX).forEach { label ->
            drawLabel(label.item, Offset(label.left, label.top))
        }
    }
}

/** With a selection: the other suburbs dim slightly and the selected one is tinted, under the outlines. */
private fun DrawScope.drawHighlight(prepared: PreparedSuburbTile, tile: PlacedTile, selectedSuburbId: String?) {
    if (selectedSuburbId == null) return
    val scale = tile.size / prepared.extent
    clipRect(tile.left, tile.top, tile.left + tile.size, tile.top + tile.size) {
        withTransform({
            translate(tile.left, tile.top)
            scale(scale, scale, Offset.Zero)
        }) {
            for ((id, path) in prepared.bySuburb) {
                drawPath(path, if (id == selectedSuburbId) SuburbStyle.selectedFillColor else SuburbStyle.dimColor)
            }
        }
    }
}

private fun DrawScope.drawOutlines(prepared: PreparedSuburbTile, tile: PlacedTile, zoom: Double, selectedSuburbId: String?) {
    val outlines = prepared.outlines ?: return
    val scale = tile.size / prepared.extent
    val color = SuburbStyle.outlineColor.copy(alpha = SuburbStyle.outlineOpacity(zoom))
    clipRect(tile.left, tile.top, tile.left + tile.size, tile.top + tile.size) {
        withTransform({
            translate(tile.left, tile.top)
            scale(scale, scale, Offset.Zero)
        }) {
            // A stroke that is a fixed width on screen even though the canvas is scaled to tile units.
            drawPath(outlines, color, style = Stroke(width = SuburbStyle.OUTLINE_WIDTH_DP * density / scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // The selected suburb's outline is thick and solid, whatever the zoom.
            selectedSuburbId?.let { prepared.bySuburb[it] }?.let { selected ->
                drawPath(selected, SuburbStyle.outlineColor, style = Stroke(width = SuburbStyle.SELECTED_OUTLINE_WIDTH_DP * density / scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private fun collectLabels(
    prepared: PreparedSuburbTile,
    tile: PlacedTile,
    measurer: TextMeasurer,
    layouts: HashMap<String, TextLayoutResult>,
    out: MutableList<LabelCandidate<TextLayoutResult>>,
) {
    val scale = tile.size / prepared.extent
    for (label in prepared.labels) {
        val layout = layouts.getOrPut(label.name) {
            measurer.measure(label.name, TextStyle(fontSize = SuburbStyle.LABEL_SIZE_SP.sp, fontWeight = FontWeight.SemiBold))
        }
        out.add(
            LabelCandidate(
                layout,
                tile.left + label.x * scale,
                tile.top + label.y * scale,
                layout.size.width.toFloat(),
                layout.size.height.toFloat(),
                SuburbStyle.labelPriority(label.areaKm2),
            ),
        )
    }
}

private fun DrawScope.drawLabel(layout: TextLayoutResult, topLeft: Offset) {
    HALO_OFFSETS.forEach { offset -> drawText(layout, color = SuburbStyle.labelHaloColor, topLeft = topLeft + offset) }
    drawText(layout, color = SuburbStyle.labelColor, topLeft = topLeft)
}
