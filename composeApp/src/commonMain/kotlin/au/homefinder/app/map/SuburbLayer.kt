package au.homefinder.app.map

import androidx.compose.ui.graphics.Color
import au.homefinder.shared.api.TileContract

/** Where the suburb vector tiles are served from and the key they need (the API of PR 7 and 8a). */
data class SuburbTileConfig(val baseUrl: String, val apiKey: String) {
    override fun toString(): String = "SuburbTileConfig(baseUrl=$baseUrl, apiKey=***)"

    /**
     * The tile URL template. Renderers that cannot set request headers (the MapLibre style) pass [keyInQuery] so the
     * key travels as a query parameter; the others send it as the [au.homefinder.shared.api.ApiContract.API_KEY_HEADER].
     */
    fun tilesUrl(keyInQuery: Boolean): String {
        val template = baseUrl.trimEnd('/') + TileContract.suburbsPath() + "/{z}/{x}/{y}" + TileContract.TILE_SUFFIX
        return if (keyInQuery) "$template?${TileContract.KEY_QUERY_PARAMETER}=$apiKey" else template
    }
}

/**
 * How suburbs are drawn on every renderer: a thin outline from the widest zoom that fades once streets matter, and a
 * name that disappears when the map is close enough to select lots (spec: zoom 5 to 12 suburbs, 13 to 15 outline only).
 */
object SuburbStyle {
    val outlineColor = Color(0xFF6D5BA6)
    val labelColor = Color(0xFF3B2F63)
    val labelHaloColor = Color(0xE6FFFFFF)

    const val OUTLINE_WIDTH_DP: Float = 1.2f
    const val OUTLINE_FULL_OPACITY_UNTIL_ZOOM: Double = 12.0
    const val OUTLINE_FADED_FROM_ZOOM: Double = 15.0
    const val OUTLINE_FADED_OPACITY: Float = 0.35f
    const val LABEL_MAX_ZOOM: Double = 13.0
    const val LABEL_SIZE_SP: Float = 12f

    /** A font OpenFreeMap's glyph server has; MapLibre's default stack (Open Sans) is not available there. */
    val LABEL_FONT_STACK: List<String> = listOf("Noto Sans Bold")

    /** The outline opacity at a zoom: full until zoom 12, fading linearly to a faint line by zoom 15. */
    fun outlineOpacity(zoom: Double): Float = when {
        zoom <= OUTLINE_FULL_OPACITY_UNTIL_ZOOM -> 1f
        zoom >= OUTLINE_FADED_FROM_ZOOM -> OUTLINE_FADED_OPACITY
        else -> {
            val t = ((zoom - OUTLINE_FULL_OPACITY_UNTIL_ZOOM) / (OUTLINE_FADED_FROM_ZOOM - OUTLINE_FULL_OPACITY_UNTIL_ZOOM)).toFloat()
            1f + (OUTLINE_FADED_OPACITY - 1f) * t
        }
    }

    fun labelsVisible(zoom: Double): Boolean = zoom >= TileContract.SUBURBS_MIN_ZOOM && zoom < LABEL_MAX_ZOOM + 1

    fun outlineVisible(zoom: Double): Boolean = zoom >= TileContract.SUBURBS_MIN_ZOOM

    /** Bigger suburbs are labelled first when names collide: a lower number wins. */
    fun labelPriority(areaKm2: Double): Int = -(areaKm2 * AREA_PRIORITY_SCALE).toInt()

    private const val AREA_PRIORITY_SCALE = 100
}
