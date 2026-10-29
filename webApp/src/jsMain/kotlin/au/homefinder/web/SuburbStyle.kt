package au.homefinder.web

import au.homefinder.shared.api.TileContract

/**
 * How suburbs are drawn: a thin purple outline that fades from zoom 12 to 15, a name that goes away once lots are
 * selectable, and a tint on the selection with the rest dimmed (spec; the same look as ADR 0015 on the old clients).
 */
object SuburbStyle {
    const val SOURCE_ID: String = "suburbs"
    const val DIM_LAYER: String = "suburb-dim"
    const val SELECTED_FILL_LAYER: String = "suburb-selected-fill"
    const val OUTLINE_LAYER: String = "suburb-outline"
    const val SELECTED_OUTLINE_LAYER: String = "suburb-selected-outline"
    const val NAME_LAYER: String = "suburb-name"

    private const val OUTLINE = "rgba(109,91,166,1)"
    private const val OUTLINE_FADED = "rgba(109,91,166,0.35)"
    private const val LABEL = "#3b2f63"
    private const val LABEL_HALO = "rgba(255,255,255,0.9)"
    private const val SELECTED_FILL = "rgba(109,91,166,0.18)"
    private const val DIM = "rgba(0,0,0,0.12)"
    private const val OUTLINE_WIDTH = 1.2
    private const val SELECTED_OUTLINE_WIDTH = 3.5
    private const val OUTLINE_FULL_UNTIL_ZOOM = 12
    private const val OUTLINE_FADED_FROM_ZOOM = 15
    private const val LABEL_MAX_ZOOM = 14
    private const val LABEL_SIZE = 12
    private const val SUBURB_ID = "suburb_id"

    /** OpenFreeMap's glyph server has this font; MapLibre's default stack is not served there. */
    private val LABEL_FONT = arrayOf("Noto Sans Bold")

    /** A filter that matches no suburb, used while nothing is selected. */
    private val MATCH_NOTHING: dynamic = jsArray("==", jsArray("get", SUBURB_ID), "")

    fun selectedFilter(suburbId: String?): dynamic = jsArray("==", jsArray("get", SUBURB_ID), suburbId ?: "")

    fun dimFilter(suburbId: String?): dynamic =
        if (suburbId == null) MATCH_NOTHING else jsArray("!=", jsArray("get", SUBURB_ID), suburbId)

    fun source(tilesUrl: String): dynamic = jsObject(
        "type" to "vector",
        "tiles" to arrayOf(tilesUrl),
        "minzoom" to TileContract.SUBURBS_MIN_ZOOM,
        "maxzoom" to TileContract.SUBURBS_MAX_ZOOM,
    )

    private fun polygonLayer(id: String, type: String, paint: dynamic, filter: dynamic = null): dynamic {
        val layer = jsObject(
            "id" to id, "type" to type, "source" to SOURCE_ID, "source-layer" to TileContract.SUBURB_POLYGON_LAYER,
            "paint" to paint,
        )
        if (filter != null) layer["filter"] = filter
        return layer
    }

    /** Layers bottom to top; the name layer is last so labels sit over the outlines. */
    fun layers(): List<dynamic> = listOf(
        polygonLayer(DIM_LAYER, "fill", jsObject("fill-color" to DIM), dimFilter(null)),
        polygonLayer(SELECTED_FILL_LAYER, "fill", jsObject("fill-color" to SELECTED_FILL), selectedFilter(null)),
        polygonLayer(
            OUTLINE_LAYER, "line",
            jsObject(
                "line-width" to OUTLINE_WIDTH,
                // The fade is in the colour's alpha, as on the old clients.
                "line-color" to jsArray(
                    "interpolate", jsArray("linear"), jsArray("zoom"),
                    OUTLINE_FULL_UNTIL_ZOOM, OUTLINE, OUTLINE_FADED_FROM_ZOOM, OUTLINE_FADED,
                ),
            ),
        ),
        polygonLayer(
            SELECTED_OUTLINE_LAYER, "line",
            jsObject("line-width" to SELECTED_OUTLINE_WIDTH, "line-color" to OUTLINE), selectedFilter(null),
        ),
        jsObject(
            "id" to NAME_LAYER, "type" to "symbol", "source" to SOURCE_ID, "source-layer" to TileContract.SUBURB_LABEL_LAYER,
            "maxzoom" to LABEL_MAX_ZOOM + 1,
            "layout" to jsObject(
                "text-field" to jsArray("get", "name"),
                "text-font" to LABEL_FONT,
                "text-size" to LABEL_SIZE,
                // Bigger suburbs are placed first (a lower sort key wins), so they keep their names where names collide.
                "symbol-sort-key" to jsArray("-", 0, jsArray("get", "area_km2")),
            ),
            "paint" to jsObject("text-color" to LABEL, "text-halo-color" to LABEL_HALO, "text-halo-width" to 1.5),
        ),
    )
}
