package au.homefinder.app.ui

import au.homefinder.app.selection.ArrangedRow
import au.homefinder.app.selection.CardGroups
import au.homefinder.app.selection.CardLayout
import au.homefinder.app.selection.CardRow
import au.homefinder.app.selection.SuburbCardRows
import au.homefinder.app.selection.SuburbCardState
import au.homefinder.app.selection.UnknownRows
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
import kotlinx.html.DL
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.DETAILS
import kotlinx.html.details
import kotlinx.html.summary
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
    const val SHOW_UNKNOWN: String = "show-unknown"
    const val HIDE_UNKNOWN: String = "hide-unknown"
}

/** The close button shows a cross; its aria-label carries the words. */
const val CLOSE_BUTTON_TEXT: String = "×"

/** The link's words say where it goes: it leaves this app. */
const val HOMELY_LINK_TEXT: String = "Reviews on Homely (opens homely.com.au)"

/** The link that opens the rating form, which stays hidden until then. */
const val RATING_EDIT_TEXT: String = "Edit the Homely rating"

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

/** What sits inside a row's closed "Source" section: where the value comes from, when, and how far to trust it. */
fun provenanceLine(row: CardRow): String =
    listOfNotNull(row.source, row.asOf?.let { "as of $it" }, confidenceLabel(row.confidence)).joinToString(" · ")

/** Heading of the collapsed section that holds a row's source, so the card stays short until you ask. */
const val SOURCE_SUMMARY_TEXT: String = "Source"

/** Heading of the collapsed section that holds a row's explanatory note (sales counts, caveats). */
const val DETAILS_SUMMARY_TEXT: String = "Details"

/**
 * A row's note and provenance, each in a `details` that is closed by default, the note ("Details") above the "Source".
 * Call through the list: `dd` is only defined there.
 */
fun DL.provenanceDetails(row: CardRow) {
    val list = this
    row.note?.let { note ->
        list.dd("note") {
            details("note-details") {
                summary { +DETAILS_SUMMARY_TEXT }
                p { +note }
            }
        }
    }
    list.dd("provenance") {
        details("source-details") {
            summary { +SOURCE_SUMMARY_TEXT }
            p { +provenanceLine(row) }
        }
    }
}

/**
 * The Suburb Card as plain HTML (ADR 0019). Unknown values read "Unknown" in italics, never blank; every row names its
 * source, as-of date and confidence. Buttons carry a [CardActions.ATTRIBUTE] the page wires up.
 */
fun FlowContent.suburbCard(
    card: SuburbCardState,
    layout: CardLayout = CardLayout(),
    customising: Boolean = false,
    showUnknown: Boolean = false,
    groupOpen: Map<String, Boolean> = emptyMap(),
) {
    section("suburb-card") {
        attributes["aria-label"] = "Suburb card"
        // The name, postcode, close button and Customise bar stay pinned at the top while the rows scroll (ADR 0053).
        div(STICKY_TOP_CLASS) {
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
            if (card is SuburbCardState.Loaded) layoutBar(customising, layout.isDefault)
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
                // Customising shows every row (hidden ones dimmed) with its controls; otherwise only the rows not hidden, and not the unknown ones
                // unless you asked to see them (ADR 0058).
                val groups = layout.arrangeGroups(SuburbCardRows.of(card.report), CardGroups.SUBURB)
                val full = groups.flatMap { it.rows }
                val arranged = UnknownRows.visible(full, customising, showUnknown)
                if (arranged.isEmpty() && UnknownRows.unknownCount(full, customising) == 0) p("hint") { +ALL_HIDDEN_HINT }
                groups.forEachIndexed { gi, group ->
                    val drawn = UnknownRows.visible(group.rows, customising, showUnknown)
                    // Walk every row, so the for-sale links under a price row are not lost when that row has no value and is left off (ADR 0061).
                    val linkOnly = group.rows.any { r -> drawn.none { it.row.label == r.row.label } && !r.hidden && ListingLinks.forSuburbRow(r.row.label, card.report) != null }
                    if (drawn.isEmpty() && !linkOnly) return@forEachIndexed
                    cardGroup(group.title, gi, groups.size, customising, open = groupOpen[group.title] ?: group.openByDefault) {
                        dl("rows") {
                            group.rows.forEach { item ->
                                val i = drawn.indexOfFirst { it.row.label == item.row.label }
                                val links = ListingLinks.forSuburbRow(item.row.label, card.report)
                                if (i >= 0) {
                                    val extra: (kotlinx.html.DIV.() -> Unit)? = when {
                                        item.row.label == SuburbCardRows.SCHOOLS_LABEL -> schoolList(card.report)
                                        item.row.label == SuburbCardRows.ZONED_LABEL -> zoneList(card.report)
                                        links != null -> ({ listingLinksLine(links) })
                                        else -> null
                                    }
                                    cardRow(item, i, drawn.size, customising, extra)
                                } else if (links != null && !item.hidden) {
                                    listingLinksRow(links)
                                }
                            }
                        }
                    }
                }
                unknownRowsNote(UnknownRows.unknownCount(full, customising), showUnknown)
                // The last section, after every row and the note about hidden ones (ADR 0060): the score you entered (when there is one), the link to the
                // suburb's reviews on Homely, and the form to edit the score. It is not a row, so Customise does not move it.
                section("homely") {
                    SuburbCardRows.homely(card.report)?.let { homely -> dl("rows") { cardRow(ArrangedRow(homely, hidden = false), 0, 1, customising = false) } }
                    ExternalLinks.homelySuburbProfile(card.report.name, card.report.postcode)?.let { url ->
                        p("external") {
                            a(href = url, target = "_blank", classes = "external-link") {
                                attributes["rel"] = "noopener noreferrer"
                                +HOMELY_LINK_TEXT
                            }
                        }
                    }
                    ratingForm(card.report)
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
 * The form for the Homely score you copy across by hand (ADR 0028), behind an "Edit the Homely rating" link (ADR 0043), filled with the current score if there is one. Remove
 * is offered only when there is something to remove. The message line starts empty; the page fills it after saving.
 */
fun FlowContent.ratingForm(report: SuburbReport) {
    val current = report.homelyRating.value
    details("rating-edit") {
        summary { +RATING_EDIT_TEXT }
        ratingFormBody(current)
    }
}

/** The form itself, drawn inside [ratingForm]'s closed `details`, so the boxes exist but stay out of the way until you ask for them. */
private fun DETAILS.ratingFormBody(current: au.homefinder.shared.model.HomelyRating?) {
    form(classes = "rating-form") {
        attributes[RatingForm.ATTRIBUTE] = "true"
        attributes["aria-label"] = "Your Homely rating"
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

/** The zones under the Zoned schools row: each school (linked to its own website when it has one) with its kind and years, the share of the suburb it covers and its ICSEA. */
private fun zoneList(report: SuburbReport): (kotlinx.html.DIV.() -> Unit)? {
    val zones = report.zonedSchools.value?.zones?.takeIf { it.isNotEmpty() } ?: return null
    return {
        ul("zones") {
            zones.forEach { zone ->
                li {
                    val address = ExternalLinks.webAddress(zone.school.website)
                    if (address != null) {
                        a(href = address, target = "_blank", classes = "external-link") {
                            attributes["rel"] = "noopener noreferrer"
                            +zone.school.name
                        }
                    } else {
                        span("school-name") { +zone.school.name }
                    }
                    span("detail") { +SuburbCardRows.zoneDetail(zone) }
                }
            }
        }
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
        list.provenanceDetails(row)
        extra?.invoke(this)
        if (customising) rowControls(item, first = index == 0, last = index == count - 1)
    }
}
