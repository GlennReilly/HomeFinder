package au.homefinder.app.ui

import au.homefinder.app.selection.ArrangedRow
import au.homefinder.app.selection.CardLayout
import au.homefinder.app.selection.LotCardRows
import au.homefinder.app.selection.LotCardState
import kotlinx.html.ButtonType
import kotlinx.html.DL
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.progress
import kotlinx.html.section
import kotlinx.html.ul

/** What the lot card says in its header: the first address, else the parcel label, else the lot id. */
fun lotCardTitle(card: LotCardState): String = when (card) {
    LotCardState.Hidden -> ""
    is LotCardState.Loading -> card.ref?.let { "Lot ${it.lotId}" } ?: "Loading lot…"
    is LotCardState.Loaded -> card.report.addresses.firstOrNull() ?: card.report.parcelLabel?.let { "Lot $it" } ?: "Lot ${card.report.lotId}"
    is LotCardState.Failed -> "Lot"
}

/** The line under the title: suburb and postcode, then the parcel label when it is not already the title. */
fun lotCardSubtitle(card: LotCardState): String? = when (card) {
    is LotCardState.Loaded -> {
        val report = card.report
        val place = listOfNotNull(report.suburbName, report.postcode).joinToString(" ")
        val parcel = report.parcelLabel?.takeIf { report.addresses.isNotEmpty() }?.let { "Parcel $it" }
        listOfNotNull(place.takeIf { it.isNotEmpty() }, parcel).joinToString(" · ").ifEmpty { null }
    }
    else -> null
}

/**
 * The basic lot card as plain HTML (ADR 0022), sharing the Suburb Card's look, buttons and provenance lines. Several
 * addresses (a strata plan) are listed under the header.
 */
fun FlowContent.lotCard(card: LotCardState, layout: CardLayout = CardLayout(), customising: Boolean = false) {
    section("suburb-card lot-card") {
        attributes["aria-label"] = "Lot card"
        div("card-header") {
            div {
                h2 { +lotCardTitle(card) }
                lotCardSubtitle(card)?.let { p("subtitle") { +it } }
            }
            button(type = ButtonType.button, classes = "close") {
                attributes[CardActions.ATTRIBUTE] = CardActions.CLOSE
                attributes["aria-label"] = "Close lot card"
                +"Close"
            }
        }
        when (card) {
            LotCardState.Hidden -> Unit
            is LotCardState.Loading -> progress { attributes["aria-label"] = "Loading lot" }
            is LotCardState.Failed -> {
                p("error") { +"Could not load this lot: ${card.reason}." }
                button(type = ButtonType.button, classes = "retry") {
                    attributes[CardActions.ATTRIBUTE] = CardActions.RETRY
                    +"Try again"
                }
            }
            is LotCardState.Loaded -> {
                if (card.report.addresses.size > 1) {
                    ul("addresses") { card.report.addresses.forEach { li { +it } } }
                }
                val arranged = layout.arrange(LotCardRows.of(card.report)).filter { customising || !it.hidden }
                layoutBar(customising, layout.isDefault)
                if (arranged.isEmpty()) p("hint") { +ALL_HIDDEN_HINT }
                dl("rows") { arranged.forEachIndexed { i, item -> lotRow(item, i, arranged.size, customising) } }
                p("disclaimer") { +CARD_DISCLAIMER }
            }
        }
    }
}

private fun DL.lotRow(item: ArrangedRow, index: Int, count: Int, customising: Boolean) {
    val list = this
    val row = item.row
    div(if (item.hidden) "row row-hidden" else "row") {
        list.dt { +row.label }
        val value = row.value
        list.dd(if (value == null) "value unknown" else "value") { +(value ?: "Unknown") }
        list.dd("provenance") { +provenanceLine(row) }
        row.note?.let { list.dd("note") { +it } }
        if (customising) rowControls(item, first = index == 0, last = index == count - 1)
    }
}
