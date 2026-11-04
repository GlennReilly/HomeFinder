package au.homefinder.app.ui

import au.homefinder.app.filter.FilterCriterion
import au.homefinder.app.filter.FilterData
import au.homefinder.app.filter.FilterResult
import au.homefinder.app.filter.Preference
import au.homefinder.app.filter.ScoreResult
import au.homefinder.app.filter.ScoreSetup
import au.homefinder.app.filter.ScoreWeight
import au.homefinder.app.filter.defaultPreference
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.ul
import kotlinx.html.option
import kotlinx.html.select
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
    scoreSection(data)
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

/** Names the page uses to find the pieces of the score section (ADR 0047). */
object ScoreIds {
    const val SUMMARY: String = "score-summary"
    const val RANKING: String = "score-ranking"

    fun weight(criterion: FilterCriterion): String = "score-${criterion.key}-weight"
    fun weightOut(criterion: FilterCriterion): String = "score-${criterion.key}-weight-value"
    fun preference(criterion: FilterCriterion): String = "score-${criterion.key}-preference"
}

const val SCORE_RANKING_SIZE: Int = 10
const val SCORE_PREFER_HIGHER_TEXT: String = "higher is better"
const val SCORE_PREFER_LOWER_TEXT: String = "lower is better"
const val SCORE_HELP: String =
    "Give each criterion a weight from 0 (ignored) to 5, and say which end is better. Each suburb is scored 0 to 100 against the other suburbs " +
        "and shaded blue on the map. A suburb with no value for a criterion is scored on the others and marked partial. Filters above still apply."

fun scoreSummaryText(result: ScoreResult): String = when {
    !result.setup.isActive -> "Give a criterion a weight above 0 to score suburbs."
    result.ranking.isEmpty() -> "No suburb has a value for the weighted criteria."
    else -> "${result.ranking.size} suburbs scored" + if (result.unscored > 0) ", ${result.unscored} with no values to score." else "."
}

/** The best suburbs first: "1. Hamilton, 82 (partial)". */
fun scoreRankingLines(result: ScoreResult, limit: Int = SCORE_RANKING_SIZE): List<String> =
    result.ranking.take(limit).mapIndexed { i, s -> "${i + 1}. ${s.name}, ${s.score}" + if (s.partial) " (partial)" else "" }

/** The score section: a weight slider and a better-end choice for each criterion that has data, then the summary and the top suburbs. */
fun FlowContent.scoreSection(data: FilterData) {
    val criteria = FilterCriterion.entries.filter(data::hasData)
    if (criteria.isEmpty()) return
    h2 { +"Score suburbs" }
    p("hint") { +SCORE_HELP }
    criteria.forEach { criterion ->
        div("score-criterion") {
            label {
                htmlFor = ScoreIds.weight(criterion)
                +"${criterion.label.substringBefore(" (")}: weight "
                span { id = ScoreIds.weightOut(criterion); +"0" }
            }
            input(InputType.range) {
                id = ScoreIds.weight(criterion)
                attributes["min"] = "0"
                attributes["max"] = ScoreWeight.MAX_WEIGHT.toString()
                attributes["step"] = "1"
                attributes["value"] = "0"
            }
            select {
                id = ScoreIds.preference(criterion)
                attributes["aria-label"] = "Which end of ${criterion.label.substringBefore(" (")} is better"
                option {
                    value = Preference.HIGHER.name
                    selected = criterion.defaultPreference == Preference.HIGHER
                    +SCORE_PREFER_HIGHER_TEXT
                }
                option {
                    value = Preference.LOWER.name
                    selected = criterion.defaultPreference == Preference.LOWER
                    +SCORE_PREFER_LOWER_TEXT
                }
            }
        }
    }
    p("filter-summary") {
        id = ScoreIds.SUMMARY
        attributes["role"] = "status"
        +scoreSummaryText(data.score(ScoreSetup()))
    }
    ul { id = ScoreIds.RANKING }
}
