package au.homefinder.web

/**
 * The colours of the two kinds of score label on the map, side by side so they stay told apart (ADR 0076). Both sit on a white halo, so each is
 * checked against white: the Homely stars are brown-orange (6.8:1), the NAPLAN figure dark blue (8.6:1, and 4.9:1 even on the basemap's light-blue water
 * without its halo). Neither is red or green, so neither reads as the green filter tint.
 */
object LabelColors {
    const val HOMELY_STARS: String = "#8a4b00"
    const val NAPLAN: String = "#0d47a1"

    /** The halo behind both, and the outline round the NAPLAN diamond. */
    const val HALO: String = "rgba(255,255,255,0.95)"
}
