package au.homefinder.app.ui

import au.homefinder.app.selection.SuburbCardRows
import au.homefinder.shared.model.NaplanRules
import au.homefinder.shared.model.School
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.span

/** The element ids and attributes of the NAPLAN controls (ADR 0068), shared by the cards, the page and the browser tests. */
object NaplanIds {
    const val EDIT_PREFIX: String = "naplan-edit-"
    const val FORM: String = "naplan-form"
    const val SCORE: String = "naplan-score"
    const val SAVE: String = "naplan-save"
    const val CLEAR: String = "naplan-clear"
    const val CANCEL: String = "naplan-cancel"
    const val MESSAGE: String = "naplan-message"

    /** On the edit link and the form: the school the figure belongs to. */
    const val CODE_ATTRIBUTE: String = "data-naplan-code"

    private val UNSAFE = Regex("[^A-Za-z0-9_-]")

    /** The edit link of a school's first listing on a card: `naplan-edit-1234`. */
    fun edit(schoolCode: String): String = EDIT_PREFIX + UNSAFE.replace(schoolCode, "_")
}

/** The link that opens the form: its words depend on whether the school has a figure yet. */
const val NAPLAN_ADD_TEXT: String = "Add NAPLAN"
const val NAPLAN_EDIT_TEXT: String = "Edit NAPLAN"

/** What the score box asks for: a signed difference from the national average (ADR 0069). */
const val NAPLAN_SCORE_LABEL: String = "Difference from national average (points, may be negative, e.g. -41)"
const val NAPLAN_SCORE_PLACEHOLDER: String = "-41"

/** The link in the form's hint: My School's home page, never a guessed page for one school. */
const val NAPLAN_MY_SCHOOL_TEXT: String = "My School (opens myschool.edu.au)"

/**
 * Gives each listing of a school on one card its own edit link id, so a school listed twice (a
 * central school zoned for both levels) never repeats an id: the first is `naplan-edit-1234`, the next `naplan-edit-1234-2`. [open] is the id of
 * the link whose form is open, or null. Make a new one for each drawing of a card.
 */
class NaplanSlots(private val open: String?) {
    private val seen = mutableMapOf<String, Int>()

    fun next(schoolCode: String): String {
        val count = (seen[schoolCode] ?: 0) + 1
        seen[schoolCode] = count
        return if (count == 1) NaplanIds.edit(schoolCode) else "${NaplanIds.edit(schoolCode)}-$count"
    }

    fun isOpen(editId: String): Boolean = editId == open
}

/** The link text, and the longer title that says the address is ours and may lead nowhere (ADR 0070). No SchoolRank data is shown. */
const val SCHOOLRANK_LINK_TEXT: String = ExternalLinks.SCHOOLRANK_SITE

fun schoolRankTitle(schoolName: String): String = "SchoolRank page for $schoolName (opens in a new tab; address built by us, may not exist)"

/** The SchoolRank link for a school, or nothing when its page address cannot be built (no locality in the data). */
fun FlowContent.schoolRankLink(school: School) {
    ExternalLinks.schoolRankPage(school.name, school.locality)?.let { externalLink(it, SCHOOLRANK_LINK_TEXT, schoolRankTitle(school.name)) }
}

/**
 * One school in a Schools row, line by line (ADR 0071): its name with [detail] (zone, level or distance text); its ICSEA when published; its NAPLAN
 * figure, if any, with the "Add NAPLAN" / "Edit NAPLAN" button (ADR 0068); its links (own website, SchoolRank, ADR 0070). When this listing's
 * button is the open one, the small form follows under the school.
 */
fun FlowContent.schoolEntry(school: School, detail: String?, slots: NaplanSlots) {
    val editId = slots.next(school.id)
    val open = slots.isOpen(editId)
    div(SchoolLines.NAME_LINE) {
        span(SchoolLines.NAME) { +school.name }
        detail?.takeIf { it.isNotBlank() }?.let { span(SchoolLines.DETAIL) { +(SchoolLines.NAME_DETAIL_SEPARATOR + it) } }
    }
    SuburbCardRows.icseaText(school)?.let { div(SchoolLines.ICSEA_LINE) { +it } }
    div(SchoolLines.NAPLAN_LINE) {
        school.naplan?.let { span(SchoolLines.NAPLAN_FIGURE) { +NaplanRules.label(it) } }
        button(type = ButtonType.button, classes = "naplan-edit") {
            id = editId
            attributes[CardActions.ATTRIBUTE] = CardActions.EDIT_NAPLAN
            attributes[NaplanIds.CODE_ATTRIBUTE] = school.id
            attributes[Aria.EXPANDED] = open.toString()
            attributes[Aria.LABEL] = "${if (school.naplan == null) NAPLAN_ADD_TEXT else NAPLAN_EDIT_TEXT} for ${school.name}"
            +(if (school.naplan == null) NAPLAN_ADD_TEXT else NAPLAN_EDIT_TEXT)
        }
    }
    val website = ExternalLinks.webAddress(school.website)
    val rank = ExternalLinks.schoolRankPage(school.name, school.locality)
    if (website != null || rank != null) {
        div(SchoolLines.LINKS_LINE) {
            website?.let { externalLink(it, SchoolLines.WEBSITE_TEXT, "${school.name}: ${SchoolLines.WEBSITE_TEXT.lowercase()} (opens in a new tab)") }
            schoolRankLink(school)
        }
    }
    if (open) naplanForm(school)
}

/** The form (the difference only: no year, no note, ADR 0071, 0073), filled with the figure already kept, if any. Clear is offered only when there is something to clear. The message line starts empty. */
private fun FlowContent.naplanForm(school: School) {
    val current = school.naplan
    form(classes = "naplan-form") {
        id = NaplanIds.FORM
        attributes[NaplanIds.CODE_ATTRIBUTE] = school.id
        attributes[Aria.LABEL] = "NAPLAN for ${school.name}"
        p("hint") {
            +"Your own figure, typed in by hand: how far the school is above (+) or below (-) the national average, for your own use. Nothing is fetched. NAPLAN results are published by ACARA on "
            externalLink(ExternalLinks.MY_SCHOOL_HOME, NAPLAN_MY_SCHOOL_TEXT)
            +"."
        }
        div("fields") {
            label {
                +NAPLAN_SCORE_LABEL
                // Plain text, not type=number or a numeric keypad: phone number pads often have no minus key, and a number box drops "+25"
                // or a typographic minus; the form checks the value itself (ADR 0069).
                input(type = InputType.text, name = NaplanIds.SCORE) {
                    id = NaplanIds.SCORE
                    attributes["autocomplete"] = "off"
                    attributes["required"] = "required"
                    attributes["placeholder"] = NAPLAN_SCORE_PLACEHOLDER
                    current?.let { attributes["value"] = NaplanRules.signed(it.score) }
                }
            }
        }
        div("buttons") {
            button(type = ButtonType.submit) {
                id = NaplanIds.SAVE
                +"Save"
            }
            if (current != null) {
                button(type = ButtonType.button) {
                    id = NaplanIds.CLEAR
                    attributes[CardActions.ATTRIBUTE] = CardActions.CLEAR_NAPLAN
                    +"Clear"
                }
            }
            button(type = ButtonType.button) {
                id = NaplanIds.CANCEL
                attributes[CardActions.ATTRIBUTE] = CardActions.CANCEL_NAPLAN
                +"Cancel"
            }
        }
        p(NaplanIds.MESSAGE) {
            id = NaplanIds.MESSAGE
            attributes[Aria.ROLE] = "status"
        }
    }
}
