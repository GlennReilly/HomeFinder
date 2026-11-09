package au.homefinder.app.ui

import au.homefinder.app.selection.SuburbCardRows
import kotlinx.html.DL
import kotlinx.html.FlowContent
import kotlinx.html.a
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

/** The attributes of a link that leaves the app (ADR 0061): a new tab, and the page opened gets neither this window nor the address it came from. */
object ExternalLinkAttributes {
    const val CLASS: String = "external-link"
    const val NEW_TAB: String = "_blank"
    const val REL: String = "rel"
    const val REL_VALUE: String = "noopener noreferrer"
    const val TITLE: String = "title"
}

/** One link out of the app with [ExternalLinkAttributes]; [title] (the tooltip and longer name) is optional. */
internal fun FlowContent.externalLink(href: String, text: String, title: String? = null) {
    a(href = href, target = ExternalLinkAttributes.NEW_TAB, classes = ExternalLinkAttributes.CLASS) {
        attributes[ExternalLinkAttributes.REL] = ExternalLinkAttributes.REL_VALUE
        title?.let { attributes[ExternalLinkAttributes.TITLE] = it }
        +text
    }
}
