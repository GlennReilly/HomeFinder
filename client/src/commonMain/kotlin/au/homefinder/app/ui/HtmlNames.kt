package au.homefinder.app.ui

import au.homefinder.app.selection.SuburbCardRows
import kotlinx.html.DL
import kotlinx.html.dd

/**
 * The accessibility attribute names the HTML builders and the page (webApp) set, in one place so they are spelt the same everywhere.
 * [ROLE] is not an `aria-*` attribute but belongs with them.
 */
object Aria {
    const val LABEL: String = "aria-label"
    const val EXPANDED: String = "aria-expanded"
    const val SELECTED: String = "aria-selected"
    const val ACTIVE_DESCENDANT: String = "aria-activedescendant"
    const val ROLE: String = "role"
}

/** The classes of a card row and its value cell, shared by the Suburb Card and the Lot Card. */
object CardRowClasses {
    const val ROW: String = "row"
    const val ROW_HIDDEN: String = "row row-hidden"
    const val VALUE: String = "value"
    const val VALUE_UNKNOWN: String = "value unknown"

    fun row(hidden: Boolean): String = if (hidden) ROW_HIDDEN else ROW
}

/** A row's value, or [SuburbCardRows.UNKNOWN] in the unknown style when there is none (never a silent blank). Call through the list: `dd` is only defined there. */
internal fun DL.valueCell(value: String?) {
    dd(if (value == null) CardRowClasses.VALUE_UNKNOWN else CardRowClasses.VALUE) { +(value ?: SuburbCardRows.UNKNOWN) }
}
