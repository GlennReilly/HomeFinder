package au.homefinder.app.ui

import au.homefinder.app.selection.CardRow
import au.homefinder.app.selection.SuburbCardRows
import au.homefinder.app.selection.SuburbCardState
import au.homefinder.shared.model.Confidence
import kotlinx.html.FlowContent
import kotlinx.html.ButtonType
import kotlinx.html.button
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.progress
import kotlinx.html.section

/** The `data-action` values the page wires to the view model. */
object CardActions {
    const val ATTRIBUTE: String = "data-action"
    const val CLOSE: String = "close"
    const val RETRY: String = "retry"
}

const val CARD_DISCLAIMER: String =
    "Indicative only. Verify against the contract of sale, a council planning certificate and a building inspection."

/** What the card says in its header for each state. */
fun cardTitle(card: SuburbCardState): String = when (card) {
    SuburbCardState.Hidden -> ""
    is SuburbCardState.Loading -> card.ref?.name ?: "Loading suburb…"
    is SuburbCardState.Loaded -> card.report.name
    is SuburbCardState.Failed -> "Suburb"
}

fun cardSubtitle(card: SuburbCardState): String? = when (card) {
    is SuburbCardState.Loading -> card.ref?.postcode
    is SuburbCardState.Loaded -> card.report.postcode
    else -> null
}?.let { "Postcode $it" }

fun confidenceLabel(confidence: Confidence): String? = when (confidence) {
    Confidence.HIGH -> "high confidence"
    Confidence.MEDIUM -> "medium confidence"
    Confidence.LOW -> "low confidence"
    Confidence.NONE -> null
}

/** The line under a row: where the value comes from, when, and how far to trust it. */
fun provenanceLine(row: CardRow): String =
    listOfNotNull("Source: ${row.source}", row.asOf?.let { "as of $it" }, confidenceLabel(row.confidence)).joinToString(" · ")

/**
 * The Suburb Card as plain HTML (ADR 0019). Unknown values read "Unknown" in italics, never blank; every row names its
 * source, as-of date and confidence. Buttons carry a [CardActions.ATTRIBUTE] the page wires up.
 */
fun FlowContent.suburbCard(card: SuburbCardState) {
    section("suburb-card") {
        attributes["aria-label"] = "Suburb card"
        div("card-header") {
            div {
                h2 { +cardTitle(card) }
                cardSubtitle(card)?.let { p("subtitle") { +it } }
            }
            button(type = ButtonType.button, classes = "close") {
                attributes[CardActions.ATTRIBUTE] = CardActions.CLOSE
                attributes["aria-label"] = "Close suburb card"
                +"Close"
            }
        }
        when (card) {
            SuburbCardState.Hidden -> Unit
            is SuburbCardState.Loading -> progress { attributes["aria-label"] = "Loading suburb" }
            is SuburbCardState.Failed -> {
                p("error") { +"Could not load this suburb: ${card.reason}." }
                button(type = ButtonType.button, classes = "retry") {
                    attributes[CardActions.ATTRIBUTE] = CardActions.RETRY
                    +"Try again"
                }
            }
            is SuburbCardState.Loaded -> {
                dl("rows") { SuburbCardRows.of(card.report).forEach { cardRow(it) } }
                p("disclaimer") { +CARD_DISCLAIMER }
            }
        }
    }
}

private fun kotlinx.html.DL.cardRow(row: CardRow) {
    val list = this
    // dt and dd are only defined on the list, so call them through it from inside the row's div.
    div("row") {
        list.dt { +row.label }
        val value = row.value
        list.dd(if (value == null) "value unknown" else "value") { +(value ?: SuburbCardRows.UNKNOWN) }
        list.dd("provenance") { +provenanceLine(row) }
        row.note?.let { list.dd("note") { +it } }
    }
}
