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

/** Classes shared by the Suburb Card, the Lot Card and the forms inside them (styles.css names them). */
object CardClasses {
    const val SUBURB_CARD: String = "suburb-card"
    const val LOT_CARD: String = "$SUBURB_CARD lot-card"
    const val HEADER: String = "card-header"
    const val SUBTITLE: String = "subtitle"
    const val SUBTITLE_PARCEL: String = "$SUBTITLE parcel-scroll"
    const val PARCEL_LINE: String = "parcel-line"
    const val CLOSE: String = "close"
    const val RETRY: String = "retry"
    const val ERROR: String = "error"
    const val HINT: String = "hint"
    const val FIELDS: String = "fields"
    const val BUTTONS: String = "buttons"
    const val ROWS: String = "rows"
    const val ADDRESSES: String = "addresses"
    const val ZONES: String = "zones"
    const val HOMELY: String = "homely"
    const val EXTERNAL: String = "external"
    const val DISCLAIMER: String = "disclaimer"
    const val SIDE_EMPTY: String = "side-empty"
}

/** Classes of the Homely rating form (the `details` that hides it and the form itself). */
object RatingClasses {
    const val EDIT: String = "rating-edit"
    const val FORM: String = "rating-form"
}

/** Classes of the NAPLAN list on the Lot Card and the NAPLAN score form. */
object NaplanClasses {
    const val EDIT: String = "naplan-edit"
    const val FORM: String = "naplan-form"
    const val SCHOOLS: String = "naplan-schools"
}

/** Classes of the filter panel. [FILTER_GROUP_CLASS] and the other top-level constants in FilterPanelHtml.kt stay where they are. */
object FilterClasses {
    const val STATUS: String = "filter-status"
    const val HEAD: String = "filter-head"
    const val RESET_ALL: String = "reset-all"
    const val GROUP_COUNT: String = "group-count"
    const val SUMMARY: String = "filter-summary"
    const val EXPORT: String = "filter-export"
    const val EXPORT_STATUS: String = "export-status"
    const val FILTER: String = "filter"
    const val RANGE: String = "range"
}

/** Classes of the realestate.com.au and domain.com.au link lines (ADR 0061). */
object ListingLinkClasses {
    const val LINKS: String = "listing-links"
    const val ROW: String = "${CardRowClasses.ROW} row-links"
    const val VALUE: String = "${CardRowClasses.VALUE} links"
}

/** Classes of the search suggestion list and its status line. */
object SearchClasses {
    const val SUGGESTIONS: String = "suggestions"
    const val SUGGESTION: String = "suggestion"
    const val HIGHLIGHTED: String = "highlighted"
    const val SUGGESTION_HIGHLIGHTED: String = "$SUGGESTION $HIGHLIGHTED"
    const val KIND: String = "kind"
    const val LABEL: String = "label"
    const val DETAIL: String = "detail"
    const val STATUS: String = "search-status"
    const val STATUS_ERROR: String = "$STATUS error"
}

/** Classes of the Customise bar and its row and group controls. */
object LayoutClasses {
    const val BAR: String = "layout-bar"
    const val BUTTON: String = "layout-button"
    const val ROW_CONTROLS: String = "row-controls"
    const val GROUP_CONTROLS: String = "group-controls"
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
