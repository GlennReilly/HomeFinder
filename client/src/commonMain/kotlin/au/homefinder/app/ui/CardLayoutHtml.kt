package au.homefinder.app.ui

import au.homefinder.app.selection.ArrangedRow
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.p

/** The names of the customise controls the page wires up (ADR 0039). */
object LayoutActions {
    const val CUSTOMISE: String = "customise"
    const val DONE: String = "customise-done"
    const val RESET: String = "layout-reset"
    const val ROW_UP: String = "row-up"
    const val ROW_DOWN: String = "row-down"
    const val ROW_TOGGLE: String = "row-toggle"

    /** Carried by a row's buttons: the row's label. */
    const val ROW_ATTRIBUTE: String = "data-row"
}

const val CUSTOMISE_HINT: String = "Hide rows or move them up and down. Your choice is kept in this browser."
const val ALL_HIDDEN_HINT: String = "Every row is hidden. Choose Customise to show some."

/** The line above the rows: the Customise button, or while customising the hint with Done and Reset. */
fun FlowContent.layoutBar(customising: Boolean, isDefault: Boolean) {
    div("layout-bar") {
        if (customising) {
            p("hint") { +CUSTOMISE_HINT }
            div("buttons") {
                actionButton(LayoutActions.DONE, "Done")
                if (!isDefault) actionButton(LayoutActions.RESET, "Reset to default")
            }
        } else {
            actionButton(LayoutActions.CUSTOMISE, "Customise")
        }
    }
}

private fun DIV.actionButton(action: String, text: String) {
    button(type = ButtonType.button, classes = "layout-button") {
        attributes[CardActions.ATTRIBUTE] = action
        +text
    }
}

/** A row's controls while customising: up, down (each off at the ends) and Hide or Show. */
fun DIV.rowControls(item: ArrangedRow, first: Boolean, last: Boolean) {
    val label = item.row.label
    div("row-controls") {
        control(LayoutActions.ROW_UP, "↑", "Move $label up", label, disabled = first)
        control(LayoutActions.ROW_DOWN, "↓", "Move $label down", label, disabled = last)
        control(LayoutActions.ROW_TOGGLE, if (item.hidden) "Show" else "Hide", (if (item.hidden) "Show " else "Hide ") + label, label, disabled = false)
    }
}

private fun DIV.control(action: String, text: String, description: String, label: String, disabled: Boolean) {
    button(type = ButtonType.button, classes = "layout-button") {
        attributes[CardActions.ATTRIBUTE] = action
        attributes[LayoutActions.ROW_ATTRIBUTE] = label
        attributes["aria-label"] = description
        if (disabled) attributes["disabled"] = "disabled"
        +text
    }
}
