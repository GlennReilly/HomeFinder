package au.homefinder.app.selection

import au.homefinder.app.filter.FilterResult

/** The two tabs of the right-hand panel (ADR 0048). */
enum class SideTab { SUBURB, FILTERS }

/**
 * Which tab of the right-hand panel is open, and whether the panel is folded down to its tab strip. It opens on Filters, so the filters are
 * the first thing seen; tapping a suburb or lot shows its card, and closing the card goes back to Filters.
 */
data class SidePanelState(val tab: SideTab = SideTab.FILTERS, val collapsed: Boolean = false) {
    /** Tapping another tab opens it; tapping the open tab folds the panel down, and tapping it again unfolds it. */
    fun tapped(target: SideTab): SidePanelState =
        if (target == tab && !collapsed) copy(collapsed = true) else SidePanelState(target, collapsed = false)

    /** A suburb or lot was selected: show its card. */
    fun cardOpened(): SidePanelState = SidePanelState(SideTab.SUBURB, collapsed = false)

    /** The selection was cleared: back to Filters, folded when the panel starts folded (a narrow window). */
    fun cardClosed(startFolded: Boolean): SidePanelState = initial(startFolded)

    companion object {
        fun initial(startFolded: Boolean): SidePanelState = SidePanelState(SideTab.FILTERS, startFolded)
    }
}

const val SUBURB_TAB_TEXT: String = "Suburb"
const val ADDRESS_TAB_TEXT: String = "Address"
const val FILTERS_TAB_TEXT: String = "Filters"

/** The first tab names what its card shows: "Address" while a lot is selected, "Suburb" otherwise (nothing selected, a suburb, loading or failed). */
fun firstTabLabel(lotShown: Boolean): String = if (lotShown) ADDRESS_TAB_TEXT else SUBURB_TAB_TEXT

/** What the Suburb tab says while no suburb or lot is selected. */
const val SIDE_EMPTY_TEXT: String = "Tap a suburb on the map to see its card, or zoom in and tap a lot."

/** The Filters tab, with the live match count while any filter is on: "Filters (12 of 173)". */
fun filtersTabLabel(result: FilterResult?): String =
    result?.passingIds?.let { "$FILTERS_TAB_TEXT (${it.size} of ${result.total})" } ?: FILTERS_TAB_TEXT
