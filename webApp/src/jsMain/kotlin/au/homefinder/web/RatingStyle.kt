package au.homefinder.web

import au.homefinder.app.selection.Formats
import au.homefinder.shared.api.TileContract
import au.homefinder.shared.model.SuburbRatingPoint

/**
 * The star score drawn under a suburb's name (ADR 0028): one point per rated suburb at the same spot as its name, with
 * the label "★ 2.1" a line below it. The points come from the ratings list, not the map tiles, so a change shows at once.
 */
object RatingStyle {
    const val SOURCE_ID: String = "suburb-ratings"
    const val LAYER_ID: String = "suburb-rating"

    private const val TEXT_COLOR = "#8a4b00"
    private const val HALO = "rgba(255,255,255,0.95)"
    private const val TEXT_SIZE = 12
    private const val HALO_WIDTH = 1.5
    private const val LINE_OFFSET_EM = 1.15
    private const val MAX_ZOOM = 15
    private val FONT = arrayOf("Noto Sans Bold")

    fun label(point: SuburbRatingPoint): String = "★ " + Formats.decimal(point.rating.stars, 1)

    fun emptySource(): dynamic = jsObject("type" to "geojson", "data" to featureCollection(emptyList()))

    fun featureCollection(points: List<SuburbRatingPoint>): dynamic = jsObject(
        "type" to "FeatureCollection",
        "features" to points.map { point ->
            jsObject(
                "type" to "Feature",
                "geometry" to jsObject("type" to "Point", "coordinates" to arrayOf(point.longitude, point.latitude)),
                "properties" to jsObject("label" to label(point), "suburb_id" to point.suburbId, "name" to point.name),
            )
        }.toTypedArray(),
    )

    /** Shown wherever suburb names are (zoom 5 up to where lots take over); allowed to overlap other labels, since few suburbs are rated. */
    fun layer(): dynamic = jsObject(
        "id" to LAYER_ID, "type" to "symbol", "source" to SOURCE_ID,
        "minzoom" to TileContract.SUBURBS_MIN_ZOOM, "maxzoom" to MAX_ZOOM,
        "layout" to jsObject(
            "text-field" to jsArray("get", "label"), "text-font" to FONT, "text-size" to TEXT_SIZE,
            "text-offset" to arrayOf(0, LINE_OFFSET_EM), "text-anchor" to "center",
            "text-allow-overlap" to true, "text-ignore-placement" to true,
        ),
        "paint" to jsObject("text-color" to TEXT_COLOR, "text-halo-color" to HALO, "text-halo-width" to HALO_WIDTH),
    )
}
