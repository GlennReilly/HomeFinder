package au.homefinder.app.map.vector

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import kotlin.math.abs
import kotlin.math.hypot

/** A label anchor in tile coordinates. */
class TileLabel(val text: String, val x: Float, val y: Float, val style: VectorStyle.LabelStyle)

/** A street name and the road it follows (x,y pairs in tile coordinates), drawn along the road. */
class TileStreet(val text: String, val points: FloatArray, val style: VectorStyle.StreetStyle)

/**
 * A decoded tile turned into draw-ready paths: one [Path] per layer and class, in tile coordinates, so drawing a frame
 * does no geometry work. Building it is done off the UI thread.
 */
class PreparedTile(
    val extent: Int,
    val paths: Map<String, Map<String?, Path>>,
    val labels: List<TileLabel>,
    val streets: List<TileStreet> = emptyList(),
) {
    companion object {
        private const val NAME_KEY = "name"
        private const val CLASS_KEY = "class"
        private const val PARK_LAYER = "park"
        private const val STREET_LAYER = "transportation_name"

        fun from(tile: VectorTile): PreparedTile {
            val paths = HashMap<String, Map<String?, Path>>()
            val parkLabels = ArrayList<TileLabel>()
            for (layerName in VectorStyle.polygonLayers + VectorStyle.lineLayers) {
                val layer = tile[layerName] ?: continue
                val wantedType = if (layerName in VectorStyle.polygonLayers) GeometryType.POLYGON else GeometryType.LINE
                val byClass = HashMap<String?, Path>()
                for (feature in layer.features) {
                    if (feature.type != wantedType) continue
                    val path = byClass.getOrPut(feature.string(CLASS_KEY)) {
                        Path().apply { if (wantedType == GeometryType.POLYGON) fillType = PathFillType.EvenOdd }
                    }
                    addParts(path, feature.geometry, wantedType == GeometryType.POLYGON)
                    if (layerName == PARK_LAYER) parkLabel(feature)?.let { parkLabels.add(it) }
                }
                if (byClass.isNotEmpty()) paths[layerName] = byClass
            }
            val labels = VectorStyle.pointLabelLayers.flatMap { layerName ->
                val textKey = VectorStyle.labelTextKey(layerName)
                tile[layerName]?.features.orEmpty().mapNotNull { feature ->
                    val text = feature.string(textKey) ?: return@mapNotNull null
                    val style = VectorStyle.labelStyle(layerName, feature.string(CLASS_KEY)) ?: return@mapNotNull null
                    val point = feature.geometry.parts.firstOrNull()?.takeIf { it.size >= 2 } ?: return@mapNotNull null
                    TileLabel(text, point[0], point[1], style)
                }
            } + parkLabels
            val streets = tile[STREET_LAYER]?.features.orEmpty().mapNotNull { feature ->
                if (feature.type != GeometryType.LINE) return@mapNotNull null
                val text = feature.string(NAME_KEY) ?: return@mapNotNull null
                val style = VectorStyle.streetStyle(feature.string(CLASS_KEY)) ?: return@mapNotNull null
                val line = feature.geometry.parts.filter { it.size >= 4 }.maxByOrNull { polylineLength(it) } ?: return@mapNotNull null
                TileStreet(text, line, style)
            }
            val extent = tile.layers.values.firstOrNull()?.extent ?: VectorTileDecoder.DEFAULT_EXTENT
            return PreparedTile(extent, paths, labels, streets)
        }

        /** A park's label sits at the centroid of its largest ring; null if it has no name or no ring. */
        private fun parkLabel(feature: VectorFeature): TileLabel? {
            val name = feature.string(NAME_KEY) ?: return null
            val style = VectorStyle.labelStyle(PARK_LAYER, null) ?: return null
            val ring = feature.geometry.parts.filter { it.size >= 6 }.maxByOrNull { abs(signedArea(it)) } ?: return null
            val (x, y) = centroid(ring)
            return TileLabel(name, x, y, style)
        }

        private fun polylineLength(line: FloatArray): Double {
            var length = 0.0
            var i = 2
            while (i + 1 < line.size) {
                length += hypot((line[i] - line[i - 2]).toDouble(), (line[i + 1] - line[i - 1]).toDouble())
                i += 2
            }
            return length
        }

        /** Twice the signed area of a ring of x,y pairs (shoelace). */
        private fun signedArea(ring: FloatArray): Double {
            var sum = 0.0
            var i = 0
            while (i + 3 < ring.size) {
                sum += ring[i].toDouble() * ring[i + 3] - ring[i + 2].toDouble() * ring[i + 1]
                i += 2
            }
            return sum
        }

        private fun centroid(ring: FloatArray): Pair<Float, Float> {
            val area = signedArea(ring)
            if (area == 0.0) return Pair(ring[0], ring[1])
            var cx = 0.0
            var cy = 0.0
            var i = 0
            while (i + 3 < ring.size) {
                val cross = ring[i].toDouble() * ring[i + 3] - ring[i + 2].toDouble() * ring[i + 1]
                cx += (ring[i] + ring[i + 2]) * cross
                cy += (ring[i + 1] + ring[i + 3]) * cross
                i += 2
            }
            return Pair((cx / (3 * area)).toFloat(), (cy / (3 * area)).toFloat())
        }

        private fun addParts(path: Path, geometry: Geometry, closeParts: Boolean) {
            for (part in geometry.parts) {
                if (part.size < 4) continue
                path.moveTo(part[0], part[1])
                var i = 2
                while (i < part.size) {
                    path.lineTo(part[i], part[i + 1])
                    i += 2
                }
                if (closeParts) path.close()
            }
        }
    }
}
