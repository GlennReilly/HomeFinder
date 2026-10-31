package au.homefinder.app.ui

import au.homefinder.app.selection.ArrangedRow
import au.homefinder.app.selection.CardLayout
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
import kotlinx.html.li
import kotlinx.html.span
import kotlinx.html.ul
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

/** The close button shows a cross; its aria-label carries the words. */
const val CLOSE_BUTTON_TEXT: String = "×"

/** The link's words say where it goes: it leaves this app. */
const val HOMELY_LINK_TEXT: String = "Reviews on Homely (opens homely.com.au)"

/** The link's words say where it goes and what to do there: the schools are named above, My School is searched by name. */
const val MY_SCHOOL_LINK_TEXT: String = "Look these schools up on My School (opens myschool.edu.au)"

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
fun FlowContent.suburbCard(card: SuburbCardState, layout: CardLayout = CardLayout(), customising: Boolean = false) {
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
                +CLOSE_BUTTON_TEXT
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
                // Customising shows every row (hidden ones dimmed) with its controls; otherwise only the rows not hidden.
                val arranged = layout.arrange(SuburbCardRows.of(card.report)).filter { customising || !it.hidden }
                layoutBar(customising, layout.isDefault)
                if (arranged.isEmpty()) p("hint") { +ALL_HIDDEN_HINT }
                // Under the price change, wherever you put it (the row's place counts even when it is hidden): the link to Homely,
                // then the score you entered (when there is one), then its entry form.
                val split = arranged.indexOfFirst { it.row.label == SuburbCardRows.PRICE_CHANGE_LABEL } + 1
                dl("rows") { arranged.take(split).forEachIndexed { i, item -> cardRow(item, i, arranged.size, customising) } }
                ExternalLinks.homelySuburbProfile(card.report.name, card.report.postcode)?.let { url ->
                    p("external") {
                        a(href = url, target = "_blank", classes = "external-link") {
                            attributes["rel"] = "noopener noreferrer"
                            +HOMELY_LINK_TEXT
                        }
                    }
                }
                SuburbCardRows.homely(card.report)?.let { homely -> dl("rows") { cardRow(ArrangedRow(homely, hidden = false), 0, 1, customising = false) } }
                ratingForm(card.report)
                dl("rows") {
                    arranged.drop(split).forEachIndexed { i, item ->
                        cardRow(item, split + i, arranged.size, customising, if (item.row.label == SuburbCardRows.SCHOOLS_LABEL) schoolList(card.report) else null)
                    }
                }
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
                    attributes["autocomplete"] = "off"
                    attributes["required"] = "required"
                    current?.let { attributes["value"] = Formats.decimal(it.stars, 1) }
                }
            }
            label {
                +"Reviews (optional)"
                input(type = InputType.text, name = RatingForm.REVIEWS_FIELD) {
                    attributes["inputmode"] = "numeric"
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

/** The schools under the Schools row: each with its kind, ICSEA and size, linked to its own website when it has one, and a link to My School. */
private fun schoolList(report: SuburbReport): (kotlinx.html.DIV.() -> Unit)? {
    val schools = report.schools.value?.publicSchoolsInside?.takeIf { it.isNotEmpty() } ?: return null
    return {
        ul("schools") {
            schools.forEach { school ->
                li {
                    val address = ExternalLinks.webAddress(school.website)
                    if (address != null) {
                        a(href = address, target = "_blank", classes = "external-link") {
                            attributes["rel"] = "noopener noreferrer"
                            +school.name
                        }
                    } else {
                        span("school-name") { +school.name }
                    }
                    span("detail") { +SuburbCardRows.schoolDetail(school) }
                }
            }
        }
        p("hint") {
            a(href = ExternalLinks.MY_SCHOOL_HOME, target = "_blank", classes = "external-link") {
                attributes["rel"] = "noopener noreferrer"
                +MY_SCHOOL_LINK_TEXT
            }
        }
    }
}

private fun kotlinx.html.DL.cardRow(
    item: ArrangedRow,
    index: Int,
    count: Int,
    customising: Boolean,
    extra: (kotlinx.html.DIV.() -> Unit)? = null,
) {
    val list = this
    val row = item.row
    // dt and dd are only defined on the list, so call them through it from inside the row's div.
    div(if (item.hidden) "row row-hidden" else "row") {
        list.dt { +row.label }
        val value = row.value
        list.dd(if (value == null) "value unknown" else "value") { +(value ?: SuburbCardRows.UNKNOWN) }
        list.dd("provenance") { +provenanceLine(row) }
        row.note?.let { list.dd("note") { +it } }
        extra?.invoke(this)
        if (customising) rowControls(item, first = index == 0, last = index == count - 1)
    }
}
