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

    /** The NAPLAN controls (ADR 0068): open a school's form, close it, and clear its figure. */
    const val EDIT_NAPLAN: String = "edit-naplan"
    const val CANCEL_NAPLAN: String = "cancel-naplan"
    const val CLEAR_NAPLAN: String = "clear-naplan"

    /** Opens the Schools group and scrolls it into view below the pinned header (ADR 0076), as a click on the suburb's NAPLAN map label does. */
    const val JUMP_SCHOOLS: String = "jump-schools"
}

/** The keyboard-reachable stand-in for clicking a NAPLAN label on the map (ADR 0076). */
const val JUMP_TO_SCHOOLS_TEXT: String = "Jump to Schools"
const val JUMP_TO_SCHOOLS_LABEL: String = "Jump to Schools: open the Schools group and scroll to it"
const val JUMP_TO_SCHOOLS_CLASS: String = "layout-button jump-schools"

/** The close button shows a cross; its aria-label carries the words. */
const val CLOSE_BUTTON_TEXT: String = "×"

/** The link's words say where it goes: it leaves this app. */
const val HOMELY_LINK_TEXT: String = "Reviews on Homely (opens homely.com.au)"

/** The link that opens the rating form, which stays hidden until then. */
const val RATING_EDIT_TEXT: String = "Edit the Homely rating"

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

/** The source part of a row's closed "Details" section: where the value comes from, when, and how far to trust it. */
fun provenanceLine(row: CardRow): String =
    listOfNotNull(row.source, row.asOf?.let { "as of $it" }, confidenceLabel(row.confidence)).joinToString(" · ")

/**
 * A row's note and its source in one `details` headed "Details", closed by default (ADR 0072): the note first, then "Source: ..." a little
 * below it. A row with neither gets no section. Call through the list: `dd` is only defined there.
 */
fun DL.provenanceDetails(row: CardRow) {
    val list = this
    val note = row.note?.trim()?.takeIf { it.isNotEmpty() }
    val source = provenanceLine(row).takeIf { it.isNotEmpty() }
    if (note == null && source == null) return
    list.dd(DetailsFold.ITEM_CLASS) { detailsFold(listOfNotNull(note), listOfNotNull(source)) }
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
    naplanOpen: String? = null,
) {
    section(CardClasses.SUBURB_CARD) {
        attributes[Aria.LABEL] = "Suburb card"
        // The name, postcode, close button and Customise bar stay pinned at the top while the rows scroll (ADR 0053).
        div(STICKY_TOP_CLASS) {
            div(CardClasses.HEADER) {
                div {
                    h2 { +cardTitle(card) }
                    cardSubtitle(card)?.let { p(CardClasses.SUBTITLE) { +it } }
                }
                button(type = ButtonType.button, classes = CardClasses.CLOSE) {
                    attributes[CardActions.ATTRIBUTE] = CardActions.CLOSE
                    attributes[Aria.LABEL] = "Close suburb card"
                    +CLOSE_BUTTON_TEXT
                }
            }
            if (card is SuburbCardState.Loaded) {
                layoutBar(customising, layout.isDefault)
                if (!customising && schoolsGroupShown(card, layout, showUnknown)) {
                    button(type = ButtonType.button, classes = JUMP_TO_SCHOOLS_CLASS) {
                        attributes[CardActions.ATTRIBUTE] = CardActions.JUMP_SCHOOLS
                        attributes[Aria.LABEL] = JUMP_TO_SCHOOLS_LABEL
                        +JUMP_TO_SCHOOLS_TEXT
                    }
                }
            }
        }
        when (card) {
            SuburbCardState.Hidden -> Unit
            is SuburbCardState.Loading -> progress { attributes[Aria.LABEL] = "Loading suburb" }
            is SuburbCardState.Failed -> {
                p(CardClasses.ERROR) { +"Could not load this suburb: ${card.reason}." }
                button(type = ButtonType.button, classes = CardClasses.RETRY) {
                    attributes[CardActions.ATTRIBUTE] = CardActions.RETRY
                    +"Try again"
                }
            }
            is SuburbCardState.Loaded -> {
                // Customising shows every row (hidden ones dimmed) with its controls; otherwise only the rows not hidden, and not the unknown ones
                // unless you asked to see them (ADR 0058).
                val groups = layout.arrangeGroups(SuburbCardRows.of(card.report), CardGroups.SUBURB)
                val slots = NaplanSlots(naplanOpen)
                val full = groups.flatMap { it.rows }
                val arranged = UnknownRows.visible(full, customising, showUnknown)
                if (arranged.isEmpty() && UnknownRows.unknownCount(full, customising) == 0) p(CardClasses.HINT) { +ALL_HIDDEN_HINT }
                groups.forEachIndexed { gi, group ->
                    val drawn = UnknownRows.visible(group.rows, customising, showUnknown)
                    // Walk every row, so the for-sale links under a price row are not lost when that row has no value and is left off (ADR 0061).
                    val linkOnly = group.rows.any { r -> drawn.none { it.row.label == r.row.label } && !r.hidden && ListingLinks.forSuburbRow(r.row.label, card.report) != null }
                    if (drawn.isEmpty() && !linkOnly) return@forEachIndexed
                    cardGroup(group.title, gi, groups.size, customising, open = groupOpen[group.title] ?: group.openByDefault) {
                        dl(CardClasses.ROWS) {
                            group.rows.forEach { item ->
                                val i = drawn.indexOfFirst { it.row.label == item.row.label }
                                val links = ListingLinks.forSuburbRow(item.row.label, card.report)
                                if (i >= 0) {
                                    val extra: (kotlinx.html.DIV.() -> Unit)? = when {
                                        item.row.label == SuburbCardRows.SCHOOLS_LABEL -> zoneList(card.report, slots)
                                        links != null -> ({ listingLinksLine(links) })
                                        else -> null
                                    }
                                    // The schools are listed one by one below, so the comma-separated names line above them says nothing new.
                                    val listed = item.row.label == SuburbCardRows.SCHOOLS_LABEL && extra != null
                                    cardRow(item, i, drawn.size, customising, extra, showValue = !listed)
                                } else if (links != null && !item.hidden) {
                                    listingLinksRow(links)
                                }
                            }
                        }
                    }
                }
                // The last section, after every row and the note about hidden ones (ADR 0060): the score you entered (when there is one), the link to the
                // suburb's reviews on Homely, and the form to edit the score. It is not a row, so Customise does not move it.
                section(CardClasses.HOMELY) {
                    SuburbCardRows.homely(card.report)?.let { homely -> dl(CardClasses.ROWS) { cardRow(ArrangedRow(homely, hidden = false), 0, 1, customising = false) } }
                    ExternalLinks.homelySuburbProfile(card.report.name, card.report.postcode)?.let { url ->
                        p(CardClasses.EXTERNAL) {
                            externalLink(url, HOMELY_LINK_TEXT)
                        }
                    }
                    ratingForm(card.report)
                }
                p(CardClasses.DISCLAIMER) { +CARD_DISCLAIMER }
            }
        }
    }
}

/** Whether the card draws a Schools group with at least one row in it (outside Customise), so the "Jump to Schools" button has somewhere to go. */
internal fun schoolsGroupShown(card: SuburbCardState.Loaded, layout: CardLayout, showUnknown: Boolean): Boolean =
    layout.arrangeGroups(SuburbCardRows.of(card.report), CardGroups.SUBURB)
        .filter { it.title == CardGroups.SCHOOLS_TITLE }
        .any { UnknownRows.visible(it.rows, customising = false, showUnknown).isNotEmpty() }

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
    details(RatingClasses.EDIT) {
        summary { +RATING_EDIT_TEXT }
        ratingFormBody(current)
    }
}

/** The form itself, drawn inside [ratingForm]'s closed `details`, so the boxes exist but stay out of the way until you ask for them. */
private fun DETAILS.ratingFormBody(current: au.homefinder.shared.model.HomelyRating?) {
    form(classes = RatingClasses.FORM) {
        attributes[RatingForm.ATTRIBUTE] = "true"
        attributes[Aria.LABEL] = "Your Homely rating"
        p(CardClasses.HINT) { +"Copy the score from the suburb's Homely page by hand. Nothing is fetched from Homely." }
        div(CardClasses.FIELDS) {
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
        div(CardClasses.BUTTONS) {
            button(type = ButtonType.submit) { +"Save" }
            if (current != null) {
                button(type = ButtonType.button) {
                    attributes[CardActions.ATTRIBUTE] = CardActions.REMOVE_RATING
                    +"Remove"
                }
            }
        }
        p(RatingForm.MESSAGE_CLASS) { attributes[Aria.ROLE] = "status" }
    }
}

/** The zones under the Schools row (ADR 0073): each school with its kind and years and the share of the suburb it covers, then its ICSEA, NAPLAN and links on lines of their own. */
private fun zoneList(report: SuburbReport, slots: NaplanSlots): (kotlinx.html.DIV.() -> Unit)? {
    val zones = report.zonedSchools.value?.zones?.takeIf { it.isNotEmpty() } ?: return null
    return {
        ul(CardClasses.ZONES) {
            zones.forEach { zone ->
                li { schoolEntry(zone.school, SuburbCardRows.zoneDetail(zone), slots) }
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
    showValue: Boolean = true,
) {
    val list = this
    val row = item.row
    // dt and dd are only defined on the list, so call them through it from inside the row's div.
    div(CardRowClasses.row(item.hidden)) {
        // The Schools group's own heading already says "Schools", so the list of schools needs no second one (Customise still names the row).
        if (showValue || customising) list.dt { +row.label }
        val value = row.value
        if (showValue) list.valueCell(value)
        list.provenanceDetails(row)
        extra?.invoke(this)
        if (customising) rowControls(item, first = index == 0, last = index == count - 1)
    }
}
