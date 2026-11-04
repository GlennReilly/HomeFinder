package au.homefinder.app.ui

import au.homefinder.app.filter.FilterCriterion
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

/** Names the page uses to find the pieces of the filter panel; each criterion has its own set, built from its key. */
object FilterIds {
    const val SUMMARY: String = "filter-summary"

    fun on(criterion: FilterCriterion): String = "filter-${criterion.key}-on"
    fun min(criterion: FilterCriterion): String = "filter-${criterion.key}-min"
    fun max(criterion: FilterCriterion): String = "filter-${criterion.key}-max"
    fun minOut(criterion: FilterCriterion): String = "filter-${criterion.key}-min-value"
    fun maxOut(criterion: FilterCriterion): String = "filter-${criterion.key}-max-value"
    fun unknown(criterion: FilterCriterion): String = "filter-${criterion.key}-unknown"

    /** The criterion an element id belongs to (any of the ids above), or null. */
    fun criterionOf(elementId: String?): FilterCriterion? =
        FilterCriterion.entries.firstOrNull { elementId?.startsWith("filter-${it.key}-") == true }
}

const val FILTER_LOADING_TEXT: String = "Loading filters…"
const val FILTER_UNAVAILABLE_TEXT: String = "The filters could not be loaded."
const val FILTER_NO_DATA_TEXT: String = "No data has been loaded yet, so there is nothing to filter on."
val FILTER_NO_SCHOOLS_TEXT: String = FilterCriterion.ICSEA.notLoadedText
val FILTER_ICSEA_HELP: String = checkNotNull(FilterCriterion.ICSEA.help)

/** The line under the sliders: how many suburbs match every switched-on filter. The unknown counts sit beside each slider. */
fun filterSummaryText(result: FilterResult): String = when {
    result.passingIds == null -> "Switch on a filter to highlight matching suburbs."
    result.selection.ranges.size == 1 -> "${result.matching} of ${result.total} suburbs match."
    else -> "${result.matching} of ${result.total} suburbs match all ${result.selection.ranges.size} filters."
}

/** "1 of 3 suburbs have no ICSEA and never match." (a count that does not change as a slider moves). */
fun filterUnknownText(data: FilterData, criterion: FilterCriterion): String =
    "${data.unknown(criterion)} of ${data.total} suburbs have no ${criterion.missing} and never match."

/**
 * The panel body once the values have loaded. The inputs are drawn once; moving a slider changes only the text beside it
 * and the summary (by id), so dragging never loses focus. A criterion whose data is not loaded says so instead of showing sliders.
 */
fun FlowContent.filterPanel(data: FilterData, initial: FilterResult) {
    h2 { +"Filter suburbs" }
    if (!data.hasAnyData) {
        p("filter-status") { +(if (data.total == 0) FILTER_NO_SCHOOLS_TEXT else FILTER_NO_DATA_TEXT) }
        return
    }
    FilterCriterion.entries.forEach { criterion ->
        val bounds = data.bounds(criterion)
        if (bounds == null) {
            p("filter-status") { +criterion.notLoadedText }
            return@forEach
        }
        div("filter") {
            label {
                input(InputType.checkBox) { id = FilterIds.on(criterion) }
                +" ${criterion.label}"
            }
            div("range") {
                label {
                    htmlFor = FilterIds.min(criterion)
                    +"At least "
                    span { id = FilterIds.minOut(criterion); +criterion.format(bounds.min) }
                }
                rangeInput(FilterIds.min(criterion), criterion, bounds.min, bounds.max, bounds.min)
                label {
                    htmlFor = FilterIds.max(criterion)
                    +"At most "
                    span { id = FilterIds.maxOut(criterion); +criterion.format(bounds.max) }
                }
                rangeInput(FilterIds.max(criterion), criterion, bounds.min, bounds.max, bounds.max)
            }
            p("hint") {
                id = FilterIds.unknown(criterion)
                +filterUnknownText(data, criterion)
            }
            criterion.help?.let { help -> p("hint") { +help } }
        }
    }
    p("filter-summary") {
        id = FilterIds.SUMMARY
        attributes["role"] = "status"
        +filterSummaryText(initial)
    }
    FilterCriterion.entries.forEach { criterion ->
        data.source(criterion)?.takeIf { data.hasData(criterion) }?.let { source ->
            p("hint") {
                val details = listOfNotNull("Source: ${source.source}", source.asOf?.let { "as of $it" }).joinToString(" · ")
                +"${criterion.label.substringBefore(" (")}: $details"
            }
        }
    }
}

private fun FlowContent.rangeInput(id: String, criterion: FilterCriterion, min: Double, max: Double, value: Double) {
    input(InputType.range) {
        this.id = id
        attributes["min"] = min.toLong().toString()
        attributes["max"] = max.toLong().toString()
        attributes["step"] = criterion.step.toLong().toString()
        attributes["value"] = value.toLong().toString()
    }
}
