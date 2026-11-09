package au.homefinder.app.ui

import au.homefinder.app.selection.ArrangedRow
import au.homefinder.app.selection.UnknownRows
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.summary

/** The names of the customise controls the page wires up (ADR 0039). */
object LayoutActions {
    const val CUSTOMISE: String = "customise"
    const val DONE: String = "customise-done"
    const val RESET: String = "layout-reset"
    const val ROW_UP: String = "row-up"
    const val ROW_DOWN: String = "row-down"
    const val ROW_TOGGLE: String = "row-toggle"
    const val GROUP_UP: String = "group-up"
    const val GROUP_DOWN: String = "group-down"

    /** Carried by a group's `details` and its buttons: the group's title. */
    const val GROUP_ATTRIBUTE: String = "data-group"

    /** Carried by a row's buttons: the row's label. */
    const val ROW_ATTRIBUTE: String = "data-row"
}

/** The class of the block at the top of a card (header and Customise bar) that stays in view while the rows scroll. */
const val STICKY_TOP_CLASS: String = "card-sticky"

const val CUSTOMISE_HINT: String = "Hide rows, or move rows and groups up and down. Your choice is kept in this browser."
const val ALL_HIDDEN_HINT: String = "Every row is hidden. Choose Customise to show some."

/** The line above the rows: the Customise button, or while customising the hint with Done and Reset. */
fun FlowContent.layoutBar(customising: Boolean, isDefault: Boolean) {
    div("layout-bar") {
        if (customising) {
            p("hint") { +CUSTOMISE_HINT }
            div("buttons") {
                actionButton(LayoutActions.DONE, "Done")
                if (!isDefault) actionButton(LayoutActions.RESET, "Reset to default", "Reset to default: this card's rows go back to the standard layout")
            }
        } else {
            actionButton(LayoutActions.CUSTOMISE, "Customise", "Customise this card's rows: hide, show and re-order")
        }
    }
}

private fun DIV.actionButton(action: String, text: String, description: String = text) {
    button(type = ButtonType.button, classes = "layout-button") {
        attributes[CardActions.ATTRIBUTE] = action
        attributes["title"] = description
        attributes[Aria.LABEL] = description
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
        attributes[Aria.LABEL] = description
        if (disabled) attributes["disabled"] = "disabled"
        +text
    }
}

/** The class of a group's `details` (ADR 0064). */
const val CARD_GROUP_CLASS: String = "card-group"

/**
 * A card group (ADR 0064): a `details` headed by its title, open when [open] says so. While customising it is always open and its heading carries
 * up/down buttons for the group. [content] draws the group's rows.
 */
fun FlowContent.cardGroup(title: String, index: Int, count: Int, customising: Boolean, open: Boolean, content: FlowContent.() -> Unit) {
    details(CARD_GROUP_CLASS) {
        attributes[LayoutActions.GROUP_ATTRIBUTE] = title
        if (open || customising) attributes["open"] = "open"
        summary { +title }
        if (customising) {
            div("group-controls") {
                groupControl(LayoutActions.GROUP_UP, "↑", "Move the $title group up", title, disabled = index == 0)
                groupControl(LayoutActions.GROUP_DOWN, "↓", "Move the $title group down", title, disabled = index == count - 1)
            }
        }
        content()
    }
}

private fun DIV.groupControl(action: String, text: String, description: String, title: String, disabled: Boolean) {
    button(type = ButtonType.button, classes = "layout-button") {
        attributes[CardActions.ATTRIBUTE] = action
        attributes[LayoutActions.GROUP_ATTRIBUTE] = title
        attributes[Aria.LABEL] = description
        if (disabled) attributes["disabled"] = "disabled"
        +text
    }
}
