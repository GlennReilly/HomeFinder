package au.homefinder.app.ui

import au.homefinder.app.selection.SuburbCardRows
import kotlinx.html.DL
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.dd
import kotlinx.html.details
import kotlinx.html.p
import kotlinx.html.summary

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
    const val DESCRIBED_BY: String = "aria-describedby"
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

/**
 * The one closed "Details" section of a card row or the filter panel (ADR 0072): the explanatory notes first, then the source lines, each source line
 * starting "Source:" so the two parts read apart.
 */
object DetailsFold {
    const val SUMMARY_TEXT: String = "Details"
    const val SOURCE_PREFIX: String = "Source: "

    /** The filter panel's lines each name their criterion first ("Source for School ICSEA: ..."). */
    const val SOURCE_FOR_PREFIX: String = "Source for "

    /** The `dd` that holds a row's section, the `details` itself, and its two kinds of paragraph. */
    const val ITEM_CLASS: String = "row-details"
    const val CLASS: String = "details-fold"
    const val NOTE_CLASS: String = "details-note"
    const val SOURCE_CLASS: String = "details-source"
}

/** Draws a closed "Details" section holding [notes] and then [sources] (each given [sourcePrefix]); nothing when both are empty. */
internal fun FlowContent.detailsFold(notes: List<String>, sources: List<String>, extraClass: String? = null, sourcePrefix: String = DetailsFold.SOURCE_PREFIX) {
    if (notes.isEmpty() && sources.isEmpty()) return
    details(listOfNotNull(DetailsFold.CLASS, extraClass).joinToString(" ")) {
        summary { +DetailsFold.SUMMARY_TEXT }
        notes.forEach { p(DetailsFold.NOTE_CLASS) { +it } }
        sources.forEach { p(DetailsFold.SOURCE_CLASS) { +(sourcePrefix + it) } }
    }
}

/**
 * The lines of one school in the Schools rows (ADR 0071): its name with the zone, level or distance text; its ICSEA (only when published); its
 * NAPLAN figure with the Add/Edit button (the class the NAPLAN button line always had); then its links.
 */
object SchoolLines {
    const val NAME_LINE: String = "school-line"
    const val NAME: String = "school-name"
    const val DETAIL: String = "detail"
    const val ICSEA_LINE: String = "school-icsea"
    const val NAPLAN_LINE: String = "naplan-line"
    const val NAPLAN_FIGURE: String = "naplan-figure"
    const val LINKS_LINE: String = "school-links"

    /** The text of the link to the school's own website, now on the links line rather than on its name. */
    const val WEBSITE_TEXT: String = "School website"
    const val NAME_DETAIL_SEPARATOR: String = " · "
}
