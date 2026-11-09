package au.homefinder.web

import au.homefinder.app.map.LonLat
import au.homefinder.app.map.NaplanLabel
import au.homefinder.shared.api.TileContract
import kotlinx.browser.document
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement

/**
 * The NAPLAN label (ADR 0076): a dark-blue diamond and the suburb's average NAPLAN figure ("+27"), the average of your own typed figures for its zoned
 * schools (ADR 0074), drawn under the suburb's name or under its Homely score label (ADR 0028) so the two never overlap. The diamond is an icon drawn by
 * the page, so it shows whatever glyphs the map's font server has. A click on it opens the suburb's Schools group. It is shown from the zoom the Homely
 * label appears at until the zoom where lots take over, so it is never on the map, nor clickable, in lot mode.
 */
object NaplanStyle {
    const val SOURCE_ID: String = "suburb-naplan"
    const val LAYER_ID: String = "suburb-naplan-label"
    const val ICON_ID: String = "naplan-diamond"
    const val SUBURB_ID: String = "suburb_id"
    const val LABEL: String = "label"
    const val RATED: String = "rated"

    const val MIN_ZOOM: Double = RatingStyle.MIN_ZOOM
    const val MAX_ZOOM: Int = TileContract.LOTS_MIN_ZOOM

    /** The cursor over a label, which can be clicked. */
    const val POINTER_CURSOR: String = "pointer"

    private const val TEXT_SIZE = 14
    private const val HALO_WIDTH = 1.6
    private val FONT = arrayOf("Noto Sans Bold")

    /** The diamond, in CSS pixels, drawn at twice that for sharp screens, with a white outline so it reads on any background. */
    private const val ICON_PX = 14
    private const val ICON_PIXEL_RATIO = 2
    private const val ICON_OUTLINE_PX = 1.5

    /** The gap between the diamond and the figure, either side of the label point. */
    private const val GAP_PX = 2.0

    /**
     * How far below the label point the NAPLAN label's middle sits: under a one-line suburb name (12 px text) or under the two-line Homely label (13 px
     * name over "★ 2.1", about 16 px each side of the point), with a small gap.
     */
    private const val BELOW_NAME_PX = 19.0
    private const val BELOW_RATED_PX = 28.0

    /** The text offset is in ems of the label's own text size. */
    private fun em(px: Double): Double = px / TEXT_SIZE

    /** The vertical offset chosen per feature: lower when the suburb has a Homely label. */
    private fun belowOffset(x: Double, rated: Double, plain: Double): dynamic =
        jsArray("case", jsArray("get", RATED), jsArray("literal", arrayOf(x, rated)), jsArray("literal", arrayOf(x, plain)))

    fun emptySource(): dynamic = jsObject("type" to "geojson", "data" to featureCollection(emptyList()))

    /** One point per label, where it is drawn; [rated] says the suburb has a Homely label there too. */
    fun featureCollection(labels: List<Triple<NaplanLabel, LonLat, Boolean>>): dynamic = jsObject(
        "type" to "FeatureCollection",
        "features" to labels.map { (label, at, rated) ->
            jsObject(
                "type" to "Feature",
                "geometry" to jsObject("type" to "Point", "coordinates" to arrayOf(at.longitude, at.latitude)),
                "properties" to jsObject(SUBURB_ID to label.suburbId, LABEL to label.text, RATED to rated),
            )
        }.toTypedArray(),
    )

    fun layer(): dynamic = jsObject(
        "id" to LAYER_ID, "type" to "symbol", "source" to SOURCE_ID, "minzoom" to MIN_ZOOM, "maxzoom" to MAX_ZOOM,
        "layout" to jsObject(
            // The diamond ends just left of the point and the figure starts just right of it, both below the name.
            "icon-image" to ICON_ID, "icon-anchor" to "right",
            "icon-offset" to belowOffset(-GAP_PX, BELOW_RATED_PX, BELOW_NAME_PX),
            "text-field" to jsArray("get", LABEL), "text-font" to FONT, "text-size" to TEXT_SIZE, "text-anchor" to "left",
            "text-offset" to belowOffset(em(GAP_PX), em(BELOW_RATED_PX), em(BELOW_NAME_PX)),
            // Always shown, like the Homely label, and never pushing other names off the map.
            "icon-allow-overlap" to true, "icon-ignore-placement" to true, "text-allow-overlap" to true, "text-ignore-placement" to true,
            "text-optional" to true,
        ),
        "paint" to jsObject("text-color" to LabelColors.NAPLAN, "text-halo-color" to LabelColors.HALO, "text-halo-width" to HALO_WIDTH),
    )

    /** Draws the diamond: a white outline, then the dark-blue fill. */
    fun icon(): dynamic {
        val size = ICON_PX * ICON_PIXEL_RATIO
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = size
        canvas.height = size
        val context = canvas.getContext("2d") as CanvasRenderingContext2D
        val middle = size / 2.0
        val inset = ICON_OUTLINE_PX * ICON_PIXEL_RATIO
        context.beginPath()
        context.moveTo(middle, inset)
        context.lineTo(size - inset, middle)
        context.lineTo(middle, size - inset)
        context.lineTo(inset, middle)
        context.closePath()
        context.lineWidth = inset * 2
        context.strokeStyle = LabelColors.HALO
        context.stroke()
        context.fillStyle = LabelColors.NAPLAN
        context.fill()
        return context.getImageData(0.0, 0.0, size.toDouble(), size.toDouble())
    }

    fun iconOptions(): dynamic = jsObject("pixelRatio" to ICON_PIXEL_RATIO)
}
