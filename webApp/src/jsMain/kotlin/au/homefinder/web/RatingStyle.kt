package au.homefinder.web

import au.homefinder.app.map.LonLat
import au.homefinder.app.selection.Formats
import au.homefinder.shared.api.TileContract
import au.homefinder.shared.model.SuburbRatingPoint

/**
 * The name of a rated suburb with its star score on a second line (ADR 0028): "FASSIFERN" over "★ 2.1". There is one label
 * per rated suburb, drawn at every zoom and allowed to overlap other labels, so it never disappears behind them. Where it
 * sits is worked out from what is on screen ([SuburbMap.placeRatingLabels]), so it stays in view as you zoom in on a big
 * suburb instead of scrolling away with a fixed point. The ordinary name layer leaves rated suburbs out, so a name is
 * never drawn twice.
 */
object RatingStyle {
    const val SOURCE_ID: String = "suburb-ratings"
    const val LAYER_ID: String = "suburb-rated-label"

    private const val NAME_COLOR = "#3b2f63"
    private const val STARS_COLOR = "#8a4b00"
    private const val HALO = "rgba(255,255,255,0.95)"
    private const val TEXT_SIZE = 13
    private const val HALO_WIDTH = 1.6
    private const val SUBURB_ID = "suburb_id"
    private val FONT = arrayOf("Noto Sans Bold")

    fun label(point: SuburbRatingPoint): String = "★ " + Formats.decimal(point.rating.stars, 1)

    /** For the ordinary name layer: everything except the rated suburbs (no filter when there are none). */
    fun unratedFilter(points: List<SuburbRatingPoint>): dynamic =
        if (points.isEmpty()) null
        else jsArray("!", jsArray("in", jsArray("get", SUBURB_ID), jsArray("literal", points.map { it.suburbId }.toTypedArray())))

    fun emptySource(): dynamic = jsObject("type" to "geojson", "data" to featureCollection(emptyList()))

    /** One point per label: where it is drawn, with the two lines of text as properties. */
    fun featureCollection(labels: List<Pair<SuburbRatingPoint, LonLat>>): dynamic = jsObject(
        "type" to "FeatureCollection",
        "features" to labels.map { (point, at) ->
            jsObject(
                "type" to "Feature",
                "geometry" to jsObject("type" to "Point", "coordinates" to arrayOf(at.longitude, at.latitude)),
                "properties" to jsObject("name" to point.name, "label" to label(point), SUBURB_ID to point.suburbId),
            )
        }.toTypedArray(),
    )

    fun layer(): dynamic = jsObject(
        "id" to LAYER_ID, "type" to "symbol", "source" to SOURCE_ID, "minzoom" to TileContract.SUBURBS_MIN_ZOOM,
        "layout" to jsObject(
            "text-field" to jsArray(
                "format",
                jsArray("get", "name"), jsObject("text-color" to NAME_COLOR),
                "\n", jsObject(),
                jsArray("get", "label"), jsObject("text-color" to STARS_COLOR),
            ),
            "text-font" to FONT, "text-size" to TEXT_SIZE, "text-anchor" to "center", "text-justify" to "center",
            "text-allow-overlap" to true, "text-ignore-placement" to true,
        ),
        "paint" to jsObject("text-color" to NAME_COLOR, "text-halo-color" to HALO, "text-halo-width" to HALO_WIDTH),
    )
}
