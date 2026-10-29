package au.homefinder.app.map.vector

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import au.homefinder.app.map.PannableMap
import au.homefinder.app.map.PlacedTile
import au.homefinder.app.map.RasterViewport
import au.homefinder.app.map.MapRenderer
import au.homefinder.app.map.TileStore
import au.homefinder.app.map.suburb.SuburbOverlay

private const val MAX_CACHED_TILES = 192
private const val LABEL_PADDING_PX = 4f
private const val HALO_OFFSET_PX = 1.5f
private val HALO_COLOR = Color(0xCCFFFFFF)
private val HALO_OFFSETS = listOf(
    Offset(-HALO_OFFSET_PX, 0f), Offset(HALO_OFFSET_PX, 0f), Offset(0f, -HALO_OFFSET_PX), Offset(0f, HALO_OFFSET_PX),
)

/**
 * Desktop fallback: OpenFreeMap vector tiles decoded and drawn by us on a Compose canvas with a simplified style,
 * so it needs no GPU interop and matches Android's data (ADR 0010).
 */
val VectorTileRenderer: MapRenderer = MapRenderer { config, modifier, interaction ->
    PannableMap(config.initialCamera, VectorTileFetcher.ATTRIBUTION, modifier, interaction.onTap) { viewport, size ->
        VectorTileLayer(viewport, size)
        config.suburbs?.let { SuburbOverlay(it, interaction.selectedSuburbId(), viewport, size) }
    }
}

private class LabelKey(val text: String, val sizeSp: Float, val italic: Boolean) {
    override fun equals(other: Any?) = other is LabelKey && text == other.text && sizeSp == other.sizeSp && italic == other.italic
    override fun hashCode() = 31 * (31 * text.hashCode() + sizeSp.hashCode()) + italic.hashCode()
}

/** A label ready to draw: its text layout, colour, rotation, and the size of the box it occupies on screen. */
private class ScreenLabel(
    val layout: TextLayoutResult,
    val color: Color,
    val angleDegrees: Float,
    val boxWidth: Float,
    val boxHeight: Float,
    val dot: Boolean,
)

@Composable
private fun BoxScope.VectorTileLayer(viewport: RasterViewport, size: IntSize) {
    val scope = rememberCoroutineScope()
    val fetcher = remember { VectorTileFetcher() }
    DisposableEffect(fetcher) { onDispose { fetcher.close() } }
    val source by produceState<TileSourceInfo?>(null, fetcher) { value = fetcher.fetchSource() }
    val store = remember(source) {
        source?.let { info -> TileStore<PreparedTile>(scope, MAX_CACHED_TILES) { fetcher.fetchTile(it, info.template) } }
    }
    val maxZoom = source?.maxZoom ?: TileJson.DEFAULT_MAX_ZOOM
    val placed = remember(viewport, size, maxZoom) {
        viewport.visibleTiles(size.width.toDouble(), size.height.toDouble(), maxZoom)
    }
    LaunchedEffect(placed, store) { store?.let { s -> placed.forEach { s.request(it.tile) } } }

    val measurer = rememberTextMeasurer()
    val layouts = remember { HashMap<LabelKey, TextLayoutResult>() }

    Canvas(Modifier.fillMaxSize()) {
        drawRect(VectorStyle.background)
        val candidates = ArrayList<LabelCandidate<ScreenLabel>>()
        placed.forEach { tile ->
            val prepared = store?.get(tile.tile) ?: return@forEach
            drawTile(prepared, tile, viewport.zoom)
            collectLabels(prepared, tile, viewport.zoom, measurer, layouts, candidates)
        }
        LabelPlacer.place(candidates, this.size.width, this.size.height, LABEL_PADDING_PX).forEach { label ->
            drawLabel(label.item, label.left, label.top)
        }
    }
}

private fun DrawScope.drawTile(prepared: PreparedTile, tile: PlacedTile, zoom: Double) {
    val scale = tile.size / prepared.extent
    clipRect(tile.left, tile.top, tile.left + tile.size, tile.top + tile.size) {
        withTransform({
            translate(tile.left, tile.top)
            scale(scale, scale, Offset.Zero)
        }) {
            for (step in VectorStyle.steps) {
                val classes = prepared.paths[step.layer] ?: continue
                when (step.kind) {
                    VectorStyle.Kind.FILL -> for ((cls, path) in classes) {
                        VectorStyle.fill(step.layer, cls, zoom)?.let { drawPath(path, it) }
                    }
                    VectorStyle.Kind.LINE -> for ((cls, path) in classes) {
                        VectorStyle.line(step.layer, cls, zoom)?.let { drawPath(path, it.color, style = stroke(it.widthPx, scale)) }
                    }
                    VectorStyle.Kind.ROAD_CASING -> for (cls in VectorStyle.roadDrawOrder) {
                        val road = VectorStyle.road(cls, zoom) ?: continue
                        val casing = road.casing ?: continue
                        classes[cls]?.let { drawPath(it, casing, style = stroke(VectorStyle.casingWidthPx(road, zoom), scale)) }
                    }
                    VectorStyle.Kind.ROAD -> for (cls in VectorStyle.roadDrawOrder) {
                        val road = VectorStyle.road(cls, zoom) ?: continue
                        classes[cls]?.let { drawPath(it, road.color, style = stroke(VectorStyle.roadWidthPx(road, zoom), scale)) }
                    }
                }
            }
        }
    }
}

/** A stroke that is [widthPx] wide on screen even though the canvas is scaled by [scale] to tile units. */
private fun stroke(widthPx: Float, scale: Float) =
    Stroke(width = widthPx / scale, cap = StrokeCap.Round, join = StrokeJoin.Round)

private const val DOT_RADIUS_PX = 2.5f
private const val DOT_SPACE_PX = 7f
private const val MAX_BEND_FRACTION = 0.6f

private fun collectLabels(
    prepared: PreparedTile,
    tile: PlacedTile,
    zoom: Double,
    measurer: TextMeasurer,
    layouts: HashMap<LabelKey, TextLayoutResult>,
    out: MutableList<LabelCandidate<ScreenLabel>>,
) {
    val scale = tile.size / prepared.extent
    fun layoutFor(text: String, sizeSp: Float, italic: Boolean): TextLayoutResult =
        layouts.getOrPut(LabelKey(text, sizeSp, italic)) {
            measurer.measure(
                text,
                TextStyle(fontSize = sizeSp.sp, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal),
            )
        }

    for (label in prepared.labels) {
        if (zoom < label.style.minZoom) continue
        val layout = layoutFor(label.text, label.style.fontSizeSp, label.style.italic)
        val width = layout.size.width.toFloat()
        val height = layout.size.height.toFloat() + if (label.style.dot) DOT_SPACE_PX else 0f
        out.add(
            LabelCandidate(
                ScreenLabel(layout, label.style.color, 0f, width, height, label.style.dot),
                tile.left + label.x * scale,
                tile.top + label.y * scale,
                width,
                height,
                label.style.priority,
                key = label.text.takeIf { label.style.repeatDistancePx > 0f },
                minSameKeyDistance = label.style.repeatDistancePx,
            ),
        )
    }

    for (street in prepared.streets) {
        if (zoom < street.style.minZoom) continue
        val layout = layoutFor(street.text, street.style.fontSizeSp, false)
        val textWidth = layout.size.width.toFloat()
        val textHeight = layout.size.height.toFloat()
        val screenPoints = FloatArray(street.points.size) { i ->
            if (i % 2 == 0) tile.left + street.points[i] * scale else tile.top + street.points[i] * scale
        }
        val anchor = StreetLabelGeometry.anchorAlongLine(screenPoints, textWidth, textHeight * MAX_BEND_FRACTION) ?: continue
        val radians = Math.toRadians(anchor.angleDegrees.toDouble())
        val boxWidth = (abs(textWidth * cos(radians)) + abs(textHeight * sin(radians))).toFloat()
        val boxHeight = (abs(textWidth * sin(radians)) + abs(textHeight * cos(radians))).toFloat()
        out.add(
            LabelCandidate(
                ScreenLabel(layout, VectorStyle.streetColor, anchor.angleDegrees, boxWidth, boxHeight, false),
                anchor.x,
                anchor.y,
                boxWidth,
                boxHeight,
                street.style.priority,
                key = street.text,
                minSameKeyDistance = VectorStyle.STREET_REPEAT_DISTANCE_PX,
            ),
        )
    }
}

private fun DrawScope.drawLabel(label: ScreenLabel, left: Float, top: Float) {
    val centre = Offset(left + label.boxWidth / 2, top + label.boxHeight / 2)
    val textTop = if (label.dot) top + DOT_SPACE_PX else centre.y - label.layout.size.height / 2
    val textLeft = centre.x - label.layout.size.width / 2
    if (label.dot) drawCircle(label.color, DOT_RADIUS_PX, Offset(centre.x, top + DOT_RADIUS_PX + 1f))
    withTransform({ rotate(label.angleDegrees, pivot = centre) }) {
        val topLeft = Offset(textLeft, textTop)
        HALO_OFFSETS.forEach { offset -> drawText(label.layout, color = HALO_COLOR, topLeft = topLeft + offset) }
        drawText(label.layout, color = label.color, topLeft = topLeft)
    }
}
