package au.homefinder.app.map.vector

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** A simplified map style for OpenMapTiles-schema tiles (OpenFreeMap), drawn by [VectorTileMap]. */
object VectorStyle {
    val background = Color(0xFFF8F4F0)
    private val water = Color(0xFFA0C8F0)

    enum class Kind { FILL, LINE, ROAD_CASING, ROAD }

    class Step(val layer: String, val kind: Kind)

    /** Back to front. */
    val steps: List<Step> = listOf(
        Step("landcover", Kind.FILL),
        Step("landuse", Kind.FILL),
        Step("park", Kind.FILL),
        Step("water", Kind.FILL),
        Step("waterway", Kind.LINE),
        Step("transportation", Kind.ROAD_CASING),
        Step("transportation", Kind.ROAD),
        Step("building", Kind.FILL),
        Step("boundary", Kind.LINE),
    )

    /** Layers whose features are drawn as filled polygons; the rest as lines. */
    val polygonLayers: Set<String> = setOf("landcover", "landuse", "park", "water", "building")
    val lineLayers: Set<String> = setOf("waterway", "transportation", "boundary")

    private const val BUILDING_MIN_ZOOM = 13.0
    private const val LANDUSE_MIN_ZOOM = 8.0
    private const val BOUNDARY_MAX_ADMIN_MIN_ZOOM = 3.0
    private const val WATERWAY_MIN_ZOOM = 9.0

    fun fill(layer: String, cls: String?, zoom: Double): Color? = when (layer) {
        "landcover" -> when (cls) {
            "wood" -> Color(0xFFD2E3BE)
            "grass", "farmland" -> Color(0xFFE4EED6)
            "wetland" -> Color(0xFFD5E8DC)
            "sand" -> Color(0xFFF2EBCB)
            "rock", "ice" -> Color(0xFFEAE6E2)
            else -> Color(0xFFE6EEDB)
        }
        "landuse" -> if (zoom < LANDUSE_MIN_ZOOM) null else when (cls) {
            "residential", "suburb" -> Color(0xFFEFEAE4)
            "cemetery", "playground", "pitch", "stadium" -> Color(0xFFDDE8CC)
            "hospital", "school", "university", "college" -> Color(0xFFF0E8E0)
            "industrial", "commercial", "retail" -> Color(0xFFE9E3EA)
            else -> Color(0xFFEBE7E0)
        }
        "park" -> Color(0xFFCFE5C0)
        "water" -> water
        "building" -> if (zoom < BUILDING_MIN_ZOOM) null else Color(0xFFDDD5CD)
        else -> null
    }

    class LineStyle(val color: Color, val widthPx: Float)

    fun line(layer: String, cls: String?, zoom: Double): LineStyle? = when (layer) {
        "waterway" -> if (zoom < WATERWAY_MIN_ZOOM) null else LineStyle(water, scaled(1.2f, zoom))
        "boundary" -> if (zoom < BOUNDARY_MAX_ADMIN_MIN_ZOOM) null else LineStyle(Color(0xFF9E9CAB), 1f)
        else -> null
    }

    class RoadStyle(val minZoom: Double, val widthAt14: Float, val color: Color, val casing: Color?)

    private val roads: Map<String, RoadStyle> = mapOf(
        "motorway" to RoadStyle(4.0, 5.0f, Color(0xFFF5A85C), Color(0xFFD98A3E)),
        "trunk" to RoadStyle(4.0, 4.5f, Color(0xFFF8C37D), Color(0xFFD9A25E)),
        "primary" to RoadStyle(6.0, 4.0f, Color(0xFFFDD99A), Color(0xFFD9B97A)),
        "secondary" to RoadStyle(8.0, 3.4f, Color(0xFFFFF3B0), Color(0xFFD9CF92)),
        "tertiary" to RoadStyle(10.0, 2.8f, Color(0xFFFFFFFF), Color(0xFFCFC8C0)),
        "minor" to RoadStyle(12.0, 2.2f, Color(0xFFFFFFFF), Color(0xFFD5CEC6)),
        "service" to RoadStyle(14.0, 1.4f, Color(0xFFFFFFFF), Color(0xFFD5CEC6)),
        "track" to RoadStyle(14.0, 1.2f, Color(0xFFE2D4B8), null),
        "path" to RoadStyle(14.0, 1.0f, Color(0xFFC9B79A), null),
        "rail" to RoadStyle(9.0, 1.2f, Color(0xFF9A9A9A), null),
    )

    /** Least to most important, so important roads draw on top. */
    val roadDrawOrder: List<String> =
        listOf("rail", "path", "track", "service", "minor", "tertiary", "secondary", "primary", "trunk", "motorway")

    private const val CASING_EXTRA_PX = 1.6f
    private const val ROAD_ZOOM_GROWTH = 0.6
    private const val MIN_ROAD_PX = 0.8f
    private const val MAX_ROAD_SCALE = 3f
    private const val REFERENCE_ZOOM = 14.0

    fun road(cls: String?, zoom: Double): RoadStyle? = roads[cls]?.takeIf { zoom >= it.minZoom }

    fun roadWidthPx(style: RoadStyle, zoom: Double): Float = scaled(style.widthAt14, zoom, MIN_ROAD_PX, style.widthAt14 * MAX_ROAD_SCALE)

    fun casingWidthPx(style: RoadStyle, zoom: Double): Float = roadWidthPx(style, zoom) + CASING_EXTRA_PX

    private fun scaled(widthAt14: Float, zoom: Double, min: Float = MIN_ROAD_PX, max: Float = Float.MAX_VALUE): Float =
        (widthAt14 * 2.0.pow((zoom - REFERENCE_ZOOM) * ROAD_ZOOM_GROWTH)).toFloat().coerceIn(min, max)

    // Labels ----------------------------------------------------------------------------------------------------

    /**
     * How a point label is drawn. [repeatDistancePx] keeps same-named labels (parks, spread over tiles) apart, and
     * [dot] marks a point of interest with a small dot above its name.
     */
    class LabelStyle(
        val minZoom: Double,
        val fontSizeSp: Float,
        val priority: Int,
        val color: Color,
        val italic: Boolean,
        val repeatDistancePx: Float = 0f,
        val dot: Boolean = false,
    )

    private val placeLabels: Map<String, LabelStyle> = mapOf(
        "country" to LabelStyle(2.0, 15f, 0, Color(0xFF333333), false),
        "state" to LabelStyle(4.0, 13f, 1, Color(0xFF555555), false),
        "city" to LabelStyle(4.0, 15f, 2, Color(0xFF222222), false),
        "town" to LabelStyle(8.0, 13f, 3, Color(0xFF333333), false),
        "village" to LabelStyle(10.0, 12f, 5, Color(0xFF444444), false),
        "suburb" to LabelStyle(11.0, 12f, 4, Color(0xFF444444), false),
        "hamlet" to LabelStyle(12.0, 11f, 6, Color(0xFF555555), false),
        "neighbourhood" to LabelStyle(13.0, 11f, 7, Color(0xFF666666), false),
    )

    private const val REPEAT_DISTANCE_PX = 400f
    private val waterLabel = LabelStyle(8.0, 11f, 8, Color(0xFF5C86B5), true)
    private val parkLabel = LabelStyle(12.0, 11f, 9, Color(0xFF4C8A4A), true, REPEAT_DISTANCE_PX)
    private val aerodromeLabel = LabelStyle(10.0, 11f, 10, Color(0xFF6A4FA0), false)
    private val peakLabel = LabelStyle(12.0, 10f, 11, Color(0xFF8A6A4A), true)
    private val poiLabel = LabelStyle(15.0, 10f, 40, Color(0xFF55606E), false, dot = true)
    /** Bus stops are numerous and add clutter, so they are not labelled. */
    private val hiddenPoiClasses: Set<String> = setOf("bus")
    private val houseNumberLabel = LabelStyle(17.0, 9f, 50, Color(0xFF7A7A7A), false)

    /** The point label style for a layer and class, or null if that feature is not labelled. */
    fun labelStyle(layer: String, cls: String?): LabelStyle? = when (layer) {
        "place" -> placeLabels[cls]
        "water_name" -> waterLabel
        "park" -> parkLabel
        "aerodrome_label" -> aerodromeLabel
        "mountain_peak" -> peakLabel
        "poi" -> if (cls in hiddenPoiClasses) null else poiLabel
        "housenumber" -> houseNumberLabel
        else -> null
    }

    /** Layers whose point features carry a label, and which property holds its text. */
    val pointLabelLayers: Set<String> = setOf("place", "water_name", "aerodrome_label", "mountain_peak", "poi", "housenumber")
    private const val HOUSE_NUMBER_KEY = "housenumber"
    private const val NAME_KEY = "name"

    fun labelTextKey(layer: String): String = if (layer == "housenumber") HOUSE_NUMBER_KEY else NAME_KEY

    /** Street names along roads, from the transportation_name layer. */
    class StreetStyle(val minZoom: Double, val fontSizeSp: Float, val priority: Int)

    private val streets: Map<String, StreetStyle> = mapOf(
        "motorway" to StreetStyle(11.0, 11f, 20),
        "trunk" to StreetStyle(11.0, 11f, 21),
        "primary" to StreetStyle(12.0, 11f, 22),
        "secondary" to StreetStyle(13.0, 10.5f, 23),
        "tertiary" to StreetStyle(14.0, 10f, 24),
        "minor" to StreetStyle(15.0, 10f, 25),
        "service" to StreetStyle(16.0, 9f, 26),
        "path" to StreetStyle(16.0, 9f, 27),
        "track" to StreetStyle(16.0, 9f, 28),
    )

    val streetColor = Color(0xFF4A4A4A)
    const val STREET_REPEAT_DISTANCE_PX = 300f

    fun streetStyle(cls: String?): StreetStyle? = streets[cls]
}
