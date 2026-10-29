package au.homefinder.app.selection

/** Where the Suburb Card goes: a bottom sheet on narrow windows, a side panel on wide ones (spec; ADR 0017). */
enum class CardLayout { BOTTOM_SHEET, SIDE_PANEL }

const val SIDE_PANEL_MIN_WIDTH_DP: Int = 600
const val SIDE_PANEL_WIDTH_DP: Int = 360

fun cardLayoutFor(windowWidthDp: Float): CardLayout =
    if (windowWidthDp < SIDE_PANEL_MIN_WIDTH_DP) CardLayout.BOTTOM_SHEET else CardLayout.SIDE_PANEL
