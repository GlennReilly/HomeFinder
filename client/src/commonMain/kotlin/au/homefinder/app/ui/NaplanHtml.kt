package au.homefinder.app.ui

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

/** The element ids and attributes of the NAPLAN controls (ADR 0068), shared by the cards, the page and the browser tests. */
object NaplanIds {
    const val EDIT_PREFIX: String = "naplan-edit-"
    const val FORM: String = "naplan-form"
    const val SCORE: String = "naplan-score"
    const val YEAR: String = "naplan-year"
    const val NOTE: String = "naplan-note"
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
 * Gives each listing of a school on one card its own edit link id, so a school listed twice (in the Schools and the Zoned schools rows, or a
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

/**
 * The "Add NAPLAN" / "Edit NAPLAN" link for one listing of a school and, when it is the open one, the small form under it (ADR 0068). The figure
 * itself is shown in the school's line beside its ICSEA.
 */
fun FlowContent.naplanControls(school: School, slots: NaplanSlots) {
    val editId = slots.next(school.id)
    val open = slots.isOpen(editId)
    div("naplan-line") {
        button(type = ButtonType.button, classes = "naplan-edit") {
            id = editId
            attributes[CardActions.ATTRIBUTE] = CardActions.EDIT_NAPLAN
            attributes[NaplanIds.CODE_ATTRIBUTE] = school.id
            attributes[Aria.EXPANDED] = open.toString()
            attributes[Aria.LABEL] = "${if (school.naplan == null) NAPLAN_ADD_TEXT else NAPLAN_EDIT_TEXT} for ${school.name}"
            +(if (school.naplan == null) NAPLAN_ADD_TEXT else NAPLAN_EDIT_TEXT)
        }
    }
    if (open) naplanForm(school)
}

/** The form, filled with the figure already kept, if any. Clear is offered only when there is something to clear. The message line starts empty. */
private fun FlowContent.naplanForm(school: School) {
    val current = school.naplan
    form(classes = "naplan-form") {
        id = NaplanIds.FORM
        attributes[NaplanIds.CODE_ATTRIBUTE] = school.id
        attributes[Aria.LABEL] = "NAPLAN for ${school.name}"
        p("hint") {
            +"Your own figure, typed in by hand: how far the school is above (+) or below (-) the national average, for your own use. Nothing is fetched. NAPLAN results are published by ACARA on "
            a(href = ExternalLinks.MY_SCHOOL_HOME, target = "_blank", classes = "external-link") {
                attributes["rel"] = "noopener noreferrer"
                +NAPLAN_MY_SCHOOL_TEXT
            }
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
            label {
                +"Year"
                input(type = InputType.text, name = NaplanIds.YEAR) {
                    id = NaplanIds.YEAR
                    attributes["inputmode"] = "numeric"
                    attributes["autocomplete"] = "off"
                    attributes["required"] = "required"
                    current?.let { attributes["value"] = it.year.toString() }
                }
            }
            label("note-field") {
                +"Note (optional)"
                input(type = InputType.text, name = NaplanIds.NOTE) {
                    id = NaplanIds.NOTE
                    attributes["autocomplete"] = "off"
                    attributes["maxlength"] = NaplanRules.MAX_NOTE_LENGTH.toString()
                    current?.note?.let { attributes["value"] = it }
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
