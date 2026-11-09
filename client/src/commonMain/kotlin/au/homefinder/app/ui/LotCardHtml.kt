package au.homefinder.app.ui

import au.homefinder.app.selection.ArrangedRow
import au.homefinder.app.selection.CardGroups
import au.homefinder.app.selection.CardLayout
import au.homefinder.app.selection.LotCardRows
import au.homefinder.app.selection.LotCardState
import au.homefinder.app.selection.SuburbCardRows
import au.homefinder.shared.model.School
import au.homefinder.app.selection.UnknownRows
import kotlinx.html.ButtonType
import kotlinx.html.DL
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.progress
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.ul

/** What the lot card says in its header: the first address, else the parcel label, else the lot id. */
fun lotCardTitle(card: LotCardState): String = when (card) {
    LotCardState.Hidden -> ""
    is LotCardState.Loading -> card.ref?.let { "Lot ${it.lotId}" } ?: "Loading lot…"
    is LotCardState.Loaded -> card.report.addresses.firstOrNull() ?: card.report.parcelLabel?.let { "Lot $it" } ?: "Lot ${card.report.lotId}"
    is LotCardState.Failed -> "Lot"
}

/** The suburb and postcode under the title, or null. */
fun lotCardPlace(card: LotCardState): String? = when (card) {
    is LotCardState.Loaded -> listOfNotNull(card.report.suburbName, card.report.postcode).joinToString(" ").ifEmpty { null }
    else -> null
}

/** The parcel label ("Parcel 1//DP100"), only when it is not already the title. */
fun lotCardParcel(card: LotCardState): String? = when (card) {
    is LotCardState.Loaded -> card.report.parcelLabel?.takeIf { card.report.addresses.isNotEmpty() }?.let { "Parcel $it" }
    else -> null
}

/** The line under the title: suburb and postcode, then the parcel label when it is not already the title. */
fun lotCardSubtitle(card: LotCardState): String? =
    listOfNotNull(lotCardPlace(card), lotCardParcel(card)).joinToString(" · ").ifEmpty { null }

/**
 * The basic lot card as plain HTML (ADR 0022), sharing the Suburb Card's look, buttons and provenance lines. Several
 * addresses (a strata plan) are listed under the header.
 */
fun FlowContent.lotCard(
    card: LotCardState,
    layout: CardLayout = CardLayout(),
    customising: Boolean = false,
    showUnknown: Boolean = false,
    groupOpen: Map<String, Boolean> = emptyMap(),
    naplanOpen: String? = null,
) {
    section(CardClasses.LOT_CARD) {
        attributes[Aria.LABEL] = "Lot card"
        div(STICKY_TOP_CLASS) {
            div(CardClasses.HEADER) {
                div {
                    h2 { +lotCardTitle(card) }
                    // On a narrow window the parcel part is hidden here and shown again below the pinned block, so it scrolls away (ADR 0067).
                    val place = lotCardPlace(card)
                    val parcel = lotCardParcel(card)
                    if (place != null || parcel != null) p(CardClasses.SUBTITLE) {
                        if (place != null) +place
                        if (parcel != null) span(CardClasses.PARCEL_LINE) { +(if (place != null) " · $parcel" else parcel) }
                    }
                }
                button(type = ButtonType.button, classes = CardClasses.CLOSE) {
                    attributes[CardActions.ATTRIBUTE] = CardActions.CLOSE
                    attributes[Aria.LABEL] = "Close lot card"
                    +CLOSE_BUTTON_TEXT
                }
            }
            if (card is LotCardState.Loaded) layoutBar(customising, layout.isDefault)
        }
        lotCardParcel(card)?.let { p(CardClasses.SUBTITLE_PARCEL) { +it } }
        when (card) {
            LotCardState.Hidden -> Unit
            is LotCardState.Loading -> progress { attributes[Aria.LABEL] = "Loading lot" }
            is LotCardState.Failed -> {
                p(CardClasses.ERROR) { +"Could not load this lot: ${card.reason}." }
                button(type = ButtonType.button, classes = CardClasses.RETRY) {
                    attributes[CardActions.ATTRIBUTE] = CardActions.RETRY
                    +"Try again"
                }
            }
            is LotCardState.Loaded -> {
                if (card.report.addresses.size > 1) {
                    ul(CardClasses.ADDRESSES) { card.report.addresses.forEach { li { +it } } }
                }
                val groups = layout.arrangeGroups(LotCardRows.of(card.report), CardGroups.LOT)
                val slots = NaplanSlots(naplanOpen)
                val full = groups.flatMap { it.rows }
                val arranged = UnknownRows.visible(full, customising, showUnknown)
                if (arranged.isEmpty() && UnknownRows.unknownCount(full, customising) == 0) p(CardClasses.HINT) { +ALL_HIDDEN_HINT }
                groups.forEachIndexed { gi, group ->
                    val drawn = UnknownRows.visible(group.rows, customising, showUnknown)
                    // Every row is walked so the address links survive a block size row that has no value and is left off (ADR 0061).
                    val linkOnly = group.rows.any { r -> drawn.none { it.row.label == r.row.label } && !r.hidden && ListingLinks.forLotRow(r.row.label, card.report) != null }
                    if (drawn.isEmpty() && !linkOnly) return@forEachIndexed
                    cardGroup(group.title, gi, groups.size, customising, open = groupOpen[group.title] ?: group.openByDefault) {
                        dl(CardClasses.ROWS) {
                            group.rows.forEach { item ->
                                val i = drawn.indexOfFirst { it.row.label == item.row.label }
                                val links = ListingLinks.forLotRow(item.row.label, card.report)
                                val schools = if (item.row.label == SuburbCardRows.SCHOOLS_LABEL) LotCardRows.schoolLines(card.report) else emptyList()
                                if (i >= 0) lotRow(item, i, drawn.size, customising, links, schools, slots) else if (links != null && !item.hidden) listingLinksRow(links)
                            }
                        }
                    }
                }
                p(CardClasses.DISCLAIMER) { +CARD_DISCLAIMER }
            }
        }
    }
}

private fun DL.lotRow(
    item: ArrangedRow,
    index: Int,
    count: Int,
    customising: Boolean,
    links: ListingBlock? = null,
    schools: List<Pair<School, String>> = emptyList(),
    slots: NaplanSlots = NaplanSlots(null),
) {
    val list = this
    val row = item.row
    div(CardRowClasses.row(item.hidden)) {
        list.dt { +row.label }
        val value = row.value
        list.valueCell(value)
        list.provenanceDetails(row)
        links?.let { listingLinksLine(it) }
        // Under the Schools row: each school it names, line by line: name and zone or distance, ICSEA, NAPLAN, links (ADR 0068, 0071).
        if (schools.isNotEmpty()) {
            ul(NaplanClasses.SCHOOLS) {
                schools.forEach { (school, detail) -> li { schoolEntry(school, detail, slots) } }
            }
        }
        if (customising) rowControls(item, first = index == 0, last = index == count - 1)
    }
}
