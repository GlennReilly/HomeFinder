package au.homefinder.app.ui

import au.homefinder.app.selection.CardRow
import au.homefinder.app.selection.SuburbCardRows
import au.homefinder.app.selection.SuburbCardState
import au.homefinder.app.selection.Formats
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.SuburbReport
import kotlinx.html.FlowContent
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.form
import kotlinx.html.h3
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.a
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
    const val REMOVE_RATING: String = "remove-rating"
}

/** The link's words say where it goes: it leaves this app. */
const val HOMELY_LINK_TEXT: String = "Reviews on Homely (opens homely.com.au)"

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
                val rows = SuburbCardRows.of(card.report)
                // Under the price change: the link to Homely, then the score you entered (when there is one), then its entry form.
                val split = rows.indexOfFirst { it.label == SuburbCardRows.PRICE_CHANGE_LABEL } + 1
                dl("rows") { rows.take(split).forEach { cardRow(it) } }
                ExternalLinks.homelySuburbProfile(card.report.name, card.report.postcode)?.let { url ->
                    p("external") {
                        a(href = url, target = "_blank", classes = "external-link") {
                            attributes["rel"] = "noopener noreferrer"
                            +HOMELY_LINK_TEXT
                        }
                    }
                }
                SuburbCardRows.homely(card.report)?.let { homely -> dl("rows") { cardRow(homely) } }
                ratingForm(card.report)
                dl("rows") { rows.drop(split).forEach { cardRow(it) } }
                p("disclaimer") { +CARD_DISCLAIMER }
            }
        }
    }
}

/** The names the page uses to find the rating form and read its boxes. */
object RatingForm {
    const val ATTRIBUTE: String = "data-rating-form"
    const val STARS_FIELD: String = "stars"
    const val REVIEWS_FIELD: String = "reviews"
    const val MESSAGE_CLASS: String = "rating-message"
}

/**
 * The form for the Homely score you copy across by hand (ADR 0028), filled with the current score if there is one. Remove
 * is offered only when there is something to remove. The message line starts empty; the page fills it after saving.
 */
fun FlowContent.ratingForm(report: SuburbReport) {
    val current = report.homelyRating.value
    form(classes = "rating-form") {
        attributes[RatingForm.ATTRIBUTE] = "true"
        attributes["aria-label"] = "Your Homely rating"
        h3 { +"Your Homely rating" }
        p("hint") { +"Copy the score from the suburb's Homely page by hand. Nothing is fetched from Homely." }
        div("fields") {
            label {
                +"Stars (0 to 5)"
                input(type = InputType.text, name = RatingForm.STARS_FIELD) {
                    attributes["inputmode"] = "decimal"
                    attributes["placeholder"] = "2.1"
                    attributes["autocomplete"] = "off"
                    attributes["required"] = "required"
                    current?.let { attributes["value"] = Formats.decimal(it.stars, 1) }
                }
            }
            label {
                +"Reviews (optional)"
                input(type = InputType.text, name = RatingForm.REVIEWS_FIELD) {
                    attributes["inputmode"] = "numeric"
                    attributes["placeholder"] = "7"
                    attributes["autocomplete"] = "off"
                    current?.reviewCount?.let { attributes["value"] = it.toString() }
                }
            }
        }
        div("buttons") {
            button(type = ButtonType.submit) { +"Save" }
            if (current != null) {
                button(type = ButtonType.button) {
                    attributes[CardActions.ATTRIBUTE] = CardActions.REMOVE_RATING
                    +"Remove"
                }
            }
        }
        p(RatingForm.MESSAGE_CLASS) { attributes["role"] = "status" }
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
