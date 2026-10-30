package au.homefinder.app.ui

import au.homefinder.app.filter.FilterData
import au.homefinder.app.filter.FilterResult
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.span

/** Names the page uses to find the pieces of the filter panel. */
object FilterIds {
    const val ICSEA_ON: String = "filter-icsea-on"
    const val ICSEA_MIN: String = "filter-icsea-min"
    const val ICSEA_MAX: String = "filter-icsea-max"
    const val ICSEA_MIN_OUT: String = "filter-icsea-min-value"
    const val ICSEA_MAX_OUT: String = "filter-icsea-max-value"
    const val SUMMARY: String = "filter-summary"
}

const val FILTER_LOADING_TEXT: String = "Loading filters…"
const val FILTER_UNAVAILABLE_TEXT: String = "The filters could not be loaded."
const val FILTER_NO_SCHOOLS_TEXT: String = "School data has not been loaded yet, so there is nothing to filter on."
const val FILTER_ICSEA_HELP: String =
    "ICSEA measures the socio-educational advantage of a school's students (1,000 is the average). It is not a measure of school quality."

/** The line under the sliders: how many suburbs match and how many have no ICSEA and so never match. */
fun filterSummaryText(result: FilterResult): String {
    val unknown = "${result.unknownIcsea} of ${result.total} suburbs have no ICSEA and never match."
    return if (result.passingIds == null) "Switch on a filter to highlight matching suburbs. $unknown"
    else "${result.matching} of ${result.total} suburbs match. $unknown"
}

/**
 * The panel body once the values have loaded. The inputs are drawn once; moving a slider changes only the text beside it
 * and the summary (by id), so dragging never loses focus.
 */
fun FlowContent.filterPanel(data: FilterData, initial: FilterResult) {
    h2 { +"Filter suburbs" }
    val bounds = data.icseaBounds
    if (bounds == null) {
        p("filter-status") { +FILTER_NO_SCHOOLS_TEXT }
        return
    }
    div("filter") {
        label {
            input(InputType.checkBox) { id = FilterIds.ICSEA_ON }
            +" School ICSEA (average of public schools in the suburb)"
        }
        div("range") {
            label {
                htmlFor = FilterIds.ICSEA_MIN
                +"At least "
                span { id = FilterIds.ICSEA_MIN_OUT; +bounds.min.toString() }
            }
            rangeInput(FilterIds.ICSEA_MIN, bounds.min, bounds.max, bounds.min)
            label {
                htmlFor = FilterIds.ICSEA_MAX
                +"At most "
                span { id = FilterIds.ICSEA_MAX_OUT; +bounds.max.toString() }
            }
            rangeInput(FilterIds.ICSEA_MAX, bounds.min, bounds.max, bounds.max)
        }
        p("hint") { +FILTER_ICSEA_HELP }
    }
    p("filter-summary") {
        id = FilterIds.SUMMARY
        attributes["role"] = "status"
        +filterSummaryText(initial)
    }
    p("hint") {
        +listOfNotNull("Source: ${data.source}", data.asOf?.let { "as of $it" }).joinToString(" · ")
    }
}

private fun FlowContent.rangeInput(id: String, min: Int, max: Int, value: Int) {
    input(InputType.range) {
        this.id = id
        attributes["min"] = min.toString()
        attributes["max"] = max.toString()
        attributes["step"] = FilterData.ICSEA_STEP.toString()
        attributes["value"] = value.toString()
    }
}
