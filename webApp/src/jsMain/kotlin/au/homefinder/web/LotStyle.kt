package au.homefinder.web

import au.homefinder.shared.api.TileContract

/**
 * How lots are drawn from zoom 16: a thin outline, the selected lot tinted with a thick outline and the rest dimmed
 * (spec zoom table: "lot boundaries" at street level; ADR 0022).
 */
object LotStyle {
    const val SOURCE_ID: String = "lots"
    const val DIM_LAYER: String = "lot-dim"
    const val SELECTED_FILL_LAYER: String = "lot-selected-fill"
    const val OUTLINE_LAYER: String = "lot-outline"
    const val SELECTED_OUTLINE_LAYER: String = "lot-selected-outline"

    private const val OUTLINE = "rgba(70,56,120,0.85)"
    private const val SELECTED_FILL = "rgba(109,91,166,0.28)"
    private const val DIM = "rgba(0,0,0,0.12)"
    private const val OUTLINE_WIDTH = 0.9
    private const val SELECTED_OUTLINE_WIDTH = 3.5
    private const val LOT_ID = "lot_id"

    fun selectedFilter(lotId: String?): dynamic = jsArray("==", jsArray("get", LOT_ID), lotId ?: "")

    fun dimFilter(lotId: String?): dynamic =
        if (lotId == null) selectedFilter(null) else jsArray("!=", jsArray("get", LOT_ID), lotId)

    fun source(tilesUrl: String): dynamic = jsObject(
        "type" to "vector",
        "tiles" to arrayOf(tilesUrl),
        "minzoom" to TileContract.LOTS_MIN_ZOOM,
        "maxzoom" to TileContract.LOTS_MAX_ZOOM,
    )

    private fun layer(id: String, type: String, paint: dynamic, filter: dynamic = null): dynamic {
        val layer = jsObject(
            "id" to id, "type" to type, "source" to SOURCE_ID, "source-layer" to TileContract.LOT_POLYGON_LAYER,
            "minzoom" to TileContract.LOTS_MIN_ZOOM, "paint" to paint,
        )
        if (filter != null) layer["filter"] = filter
        return layer
    }

    /** Bottom to top: dimming, the selection's tint, every outline, then the selected outline on top. */
    fun layers(): List<dynamic> = listOf(
        layer(DIM_LAYER, "fill", jsObject("fill-color" to DIM), dimFilter(null)),
        layer(SELECTED_FILL_LAYER, "fill", jsObject("fill-color" to SELECTED_FILL), selectedFilter(null)),
        layer(OUTLINE_LAYER, "line", jsObject("line-color" to OUTLINE, "line-width" to OUTLINE_WIDTH)),
        layer(SELECTED_OUTLINE_LAYER, "line", jsObject("line-color" to OUTLINE, "line-width" to SELECTED_OUTLINE_WIDTH), selectedFilter(null)),
    )
}
