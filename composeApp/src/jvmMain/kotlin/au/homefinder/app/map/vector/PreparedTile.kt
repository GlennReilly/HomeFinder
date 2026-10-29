package au.homefinder.app.map.vector

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType

/** A label anchor in tile coordinates. */
class TileLabel(val text: String, val x: Float, val y: Float, val style: VectorStyle.LabelStyle)

/**
 * A decoded tile turned into draw-ready paths: one [Path] per layer and class, in tile coordinates, so drawing a frame
 * does no geometry work. Building it is done off the UI thread.
 */
class PreparedTile(
    val extent: Int,
    val paths: Map<String, Map<String?, Path>>,
    val labels: List<TileLabel>,
) {
    companion object {
        private const val NAME_KEY = "name"
        private const val CLASS_KEY = "class"

        fun from(tile: VectorTile): PreparedTile {
            val paths = HashMap<String, Map<String?, Path>>()
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
                }
                if (byClass.isNotEmpty()) paths[layerName] = byClass
            }
            val labels = VectorStyle.labelLayers.flatMap { layerName ->
                tile[layerName]?.features.orEmpty().mapNotNull { feature ->
                    val text = feature.string(NAME_KEY) ?: return@mapNotNull null
                    val style = VectorStyle.labelStyle(layerName, feature.string(CLASS_KEY)) ?: return@mapNotNull null
                    val point = feature.geometry.parts.firstOrNull()?.takeIf { it.size >= 2 } ?: return@mapNotNull null
                    TileLabel(text, point[0], point[1], style)
                }
            }
            val extent = tile.layers.values.firstOrNull()?.extent ?: VectorTileDecoder.DEFAULT_EXTENT
            return PreparedTile(extent, paths, labels)
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
