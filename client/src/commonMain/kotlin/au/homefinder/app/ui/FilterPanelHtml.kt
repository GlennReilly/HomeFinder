package au.homefinder.app.ui

import au.homefinder.app.filter.FilterCriterion
import au.homefinder.app.filter.FilterData
import au.homefinder.app.filter.FilterGroups
import au.homefinder.app.filter.FilterResult
import au.homefinder.app.filter.ExportAvailability
import au.homefinder.app.filter.SuburbExport
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.details
import kotlinx.html.p
import kotlinx.html.summary
import kotlinx.html.span

/** Names the page uses to find the pieces of the filter panel; each criterion has its own set, built from its key. */
object FilterIds {
    const val SUMMARY: String = "filter-summary"
    const val RESET: String = "filter-reset"

    /** The PDF export (ADR 0080): its button, the line under it that says what it does or why it is off, and its progress line. */
    const val EXPORT: String = "filter-export"
    const val EXPORT_HINT: String = "filter-export-hint"
    const val EXPORT_STATUS: String = "filter-export-status"

    fun on(criterion: FilterCriterion): String = "filter-${criterion.key}-on"
    fun limit(criterion: FilterCriterion): String = "filter-${criterion.key}-limit"
    fun limitOut(criterion: FilterCriterion): String = "filter-${criterion.key}-limit-value"
    fun limitPrefix(criterion: FilterCriterion): String = "filter-${criterion.key}-limit-prefix"
    fun unknown(criterion: FilterCriterion): String = "filter-${criterion.key}-unknown"

    /** The criterion an element id belongs to (any of the ids above), or null. */
    fun criterionOf(elementId: String?): FilterCriterion? =
        FilterCriterion.entries.firstOrNull { elementId?.startsWith("filter-${it.key}-") == true }
}

/** The class of a filter group's `details` (ADR 0066). */
const val FILTER_GROUP_CLASS: String = "filter-group"

const val FILTER_RESET_TEXT: String = "Reset all filters"

/** The button's accessible name and tooltip: what it does, or that there is nothing to reset yet. */
fun filterResetTitle(anyOn: Boolean): String =
    if (anyOn) "Reset all filters: switch every filter off" else "Reset all filters (none changed)"
const val FILTER_LOADING_TEXT: String = "Loading filters…"
const val FILTER_UNAVAILABLE_TEXT: String = "The filters could not be loaded."
const val FILTER_NO_DATA_TEXT: String = "No data has been loaded yet, so there is nothing to filter on."
val FILTER_NO_SCHOOLS_TEXT: String = FilterCriterion.ZONED.notLoadedText

/** The line under the sliders: how many suburbs match every switched-on filter. The unknown counts sit beside each slider. */
fun filterSummaryText(result: FilterResult): String = when {
    result.passingIds == null -> "Switch on a filter to highlight matching suburbs."
    result.selection.limits.size == 1 -> "${result.matching} of ${result.total} suburbs match." + NO_MATCH_HINT.takeIf { result.matching == 0 }.orEmpty()
    else -> "${result.matching} of ${result.total} suburbs match all ${result.selection.limits.size} filters." +
        NO_MATCH_HINT.takeIf { result.matching == 0 }.orEmpty()
}

/** Added when nothing passes: the whole map is washed out then, which can look like the filters have stopped working. */
const val NO_MATCH_HINT: String = " Loosen a range, or switch a filter off."

/** "1 of 3 suburbs have no ICSEA and never match." (a count that does not change as a slider moves). */
fun filterUnknownText(data: FilterData, criterion: FilterCriterion): String =
    "${data.unknown(criterion)} of ${data.total} suburbs have no ${criterion.missing} and never match."

/**
 * The panel body once the values have loaded. The inputs are drawn once; moving a slider changes only the text beside it
 * and the summary (by id), so dragging never loses focus. A criterion whose data is not loaded says so instead of showing sliders.
 */
fun FlowContent.filterPanel(data: FilterData, initial: FilterResult) {
    if (!data.hasAnyData) {
        h2 { +"Filter suburbs" }
        p("filter-status") { +(if (data.total == 0) FILTER_NO_SCHOOLS_TEXT else FILTER_NO_DATA_TEXT) }
        return
    }
    // Reset all switches every filter off and puts each slider back to its starting end; off (disabled) while nothing is on.
    div("filter-head") {
        h2 { +"Filter suburbs" }
        button(type = ButtonType.button, classes = "reset-all") {
            id = FilterIds.RESET
            attributes[Aria.LABEL] = filterResetTitle(initial.selection.isActive)
            attributes["title"] = filterResetTitle(initial.selection.isActive)
            if (!initial.selection.isActive) attributes["disabled"] = "disabled"
            +FILTER_RESET_TEXT
        }
    }
    // Criteria sit in foldable groups (ADR 0066); a group starts open if it is the first or has a switched-on criterion.
    val on = initial.selection.limits.keys
    FilterGroups.arrange(on).forEach { group ->
        details(FILTER_GROUP_CLASS) {
            id = group.id
            if (group.open) attributes["open"] = "open"
            summary {
                +group.title
                span("group-count") { id = group.countId; +FilterGroups.onText(group, on) }
            }
            group.criteria.forEach { criterion -> filterCriterion(data, criterion) }
        }
    }
    p("filter-summary") {
        id = FilterIds.SUMMARY
        attributes[Aria.ROLE] = "status"
        +filterSummaryText(initial)
    }
    exportBlock(initial)
    val sources = FilterCriterion.entries.mapNotNull { criterion ->
        data.source(criterion)?.takeIf { data.hasData(criterion) }?.let { source ->
            val details = listOfNotNull(source.source, source.asOf?.let { "as of $it" }).joinToString(" · ")
            "${criterion.label.substringBefore(" (")}: $details"
        }
    }
    // A criterion whose limits need explaining (NAPLAN, ADR 0074) adds a note once its data is there, even with no values yet.
    val notes = FilterCriterion.entries.mapNotNull { criterion -> criterion.detailsNote?.takeIf { data.source(criterion) != null } }
    // One closed "Details" section, as on the cards (ADR 0072).
    detailsFold(notes, sources, FILTER_SOURCES_CLASS, DetailsFold.SOURCE_FOR_PREFIX)
}

/** The class of the export button; styled like the other small buttons (ADR 0077). */
const val EXPORT_BUTTON_CLASS: String = "export-pdf"

/**
 * The "Export PDF (N suburbs)" button (ADR 0080), off until a filter is on and between 1 and the cap of suburbs match; the hint under it says what it
 * does or why it is off, and the status line (empty at first) reports the export. The page keeps the button's text, state and hint up to date.
 */
fun FlowContent.exportBlock(result: FilterResult) {
    val availability = SuburbExport.availability(result)
    div("filter-export") {
        button(type = ButtonType.button, classes = EXPORT_BUTTON_CLASS) {
            id = FilterIds.EXPORT
            attributes[Aria.DESCRIBED_BY] = FilterIds.EXPORT_HINT
            if (availability !is ExportAvailability.Ready) attributes["disabled"] = "disabled"
            +SuburbExport.buttonText(availability)
        }
        p("hint") {
            id = FilterIds.EXPORT_HINT
            +SuburbExport.hintText(availability)
        }
        p("export-status") {
            id = FilterIds.EXPORT_STATUS
            attributes[Aria.ROLE] = "status"
        }
    }
}

/** The class that marks the filter panel's Details section. */
const val FILTER_SOURCES_CLASS: String = "filter-sources"

private fun FlowContent.filterCriterion(data: FilterData, criterion: FilterCriterion) {
    val bounds = data.bounds(criterion)
    if (data.isEmpty(criterion)) return emptyCriterion(data, criterion)
    if (bounds == null) {
        p("filter-status") { +criterion.notLoadedText }
        return
    }
    div("filter") {
        label {
            input(InputType.checkBox) { id = FilterIds.on(criterion) }
            +" ${criterion.label}"
        }
        div("range") {
            label {
                htmlFor = FilterIds.limit(criterion)
                span { id = FilterIds.limitPrefix(criterion); +data.limitPrefix(criterion, criterion.startLimit(bounds)) }
                span { id = FilterIds.limitOut(criterion); +data.limitText(criterion, criterion.startLimit(bounds)) }
            }
            rangeInput(FilterIds.limit(criterion), criterion, bounds.min, bounds.max, criterion.startLimit(bounds))
        }
        p("hint") {
            id = FilterIds.unknown(criterion)
            +filterUnknownText(data, criterion)
        }
        data.helpText(criterion)?.let { help -> p("hint") { +help } }
    }
}

/** The class of a criterion drawn but switched off for want of values (ADR 0074). */
const val FILTER_EMPTY_CLASS: String = "filter-empty"

/**
 * A criterion whose data is there but has no values yet (no NAPLAN figure typed, ADR 0074): its checkbox and slider are drawn disabled, so it cannot be
 * switched on, with [FilterCriterion.emptyText] where the limit would be. The ids are the usual ones, so the page needs no special case.
 */
private fun FlowContent.emptyCriterion(data: FilterData, criterion: FilterCriterion) {
    div("filter $FILTER_EMPTY_CLASS") {
        label {
            input(InputType.checkBox) { id = FilterIds.on(criterion); disabled = true }
            +" ${criterion.label}"
        }
        div("range") {
            label {
                htmlFor = FilterIds.limit(criterion)
                span { id = FilterIds.limitPrefix(criterion) }
                span { id = FilterIds.limitOut(criterion); +data.limitText(criterion, EMPTY_SLIDER_POSITION) }
            }
            input(InputType.range) {
                id = FilterIds.limit(criterion)
                disabled = true
                attributes["min"] = EMPTY_SLIDER_POSITION.toLong().toString()
                attributes["max"] = EMPTY_SLIDER_POSITION.toLong().toString()
                attributes["value"] = EMPTY_SLIDER_POSITION.toLong().toString()
            }
        }
        data.helpText(criterion)?.let { help -> p("hint") { +help } }
    }
}

/** Where a disabled, empty slider sits: it has no range, so one position. */
private const val EMPTY_SLIDER_POSITION: Double = 0.0

private fun FlowContent.rangeInput(id: String, criterion: FilterCriterion, min: Double, max: Double, value: Double) {
    input(InputType.range) {
        this.id = id
        attributes["min"] = min.toLong().toString()
        attributes["max"] = max.toLong().toString()
        attributes["step"] = criterion.step.toLong().toString()
        attributes["value"] = value.toLong().toString()
    }
}
