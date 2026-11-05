package au.homefinder.app.filter

import au.homefinder.app.selection.Formats
import au.homefinder.shared.model.SuburbFiltersResponse
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

/** One thing the panel can filter suburbs on (ADR 0035, 0046). Every value is a number in the unit shown beside its slider. */
enum class FilterCriterion(
    /** Part of the element ids (`filter-<key>-on`, `-min`, `-max`), so the page can find the pieces. */
    val key: String,
    val label: String,
    /** What a value ends with: nothing, " km" or "%". */
    val unit: String,
    /** The slider step, also the grid the slider's ends are rounded out to. */
    val step: Double,
    /** What a suburb with no value lacks, for "N of M suburbs have no <this> and never match". */
    val missing: String,
    /** A line under the slider that explains what the number is and where it falls short; null when the label says it all. */
    val help: String?,
    /** What to tell the user when this criterion's data has not been loaded. */
    val notLoadedText: String,
) {
    ICSEA(
        "icsea", "School ICSEA (average of public schools in the suburb)", "", 10.0, "ICSEA",
        "ICSEA measures the socio-educational advantage of a school's students (1,000 is the average). It is not a measure of school quality.",
        "School data has not been loaded yet, so there is nothing to filter on.",
    ),
    BEACH(
        "beach", "Distance to the beach (from the suburb's centre)", " km", 1.0, "beach distance",
        "A straight line from the suburb's centre to the nearest ocean beach on the mapped coastline, not a route.",
        "OpenStreetMap data has not been loaded yet, so there is no beach distance to filter on.",
    ),
    PARKS(
        "parks", "Park share of the suburb", "%", 1.0, "mapped park",
        "Parks of at least 0.5 ha as a share of the suburb's area. OpenStreetMap may be incomplete, and a suburb with no park mapped has no value.",
        "OpenStreetMap data has not been loaded yet, so there is no park share to filter on.",
    ),
    RENTED(
        "rented", "Homes rented", "%", 1.0, "Census figure",
        "The share of occupied homes that were rented on Census night 2021. A suburb with few homes is only roughly right.",
        "Census data has not been loaded yet, so there is nothing to filter on.",
    ),
    SEIFA(
        "seifa", "Socio-economic advantage (SEIFA decile, 10 = most advantaged)", "", 1.0, "SEIFA decile",
        "From the ABS SEIFA 2021: it describes the residents of the suburb as a group, ranked across Australia from decile 1 (most disadvantaged) to 10 (most advantaged).",
        "SEIFA data has not been loaded yet, so there is nothing to filter on.",
    );

    fun format(value: Double): String = Formats.decimal(value, 0) + unit
}

/** An inclusive range of a criterion's values. */
data class FilterRange(val min: Double, val max: Double) {
    init {
        require(min <= max) { "min $min is above max $max" }
    }

    operator fun contains(value: Double): Boolean = value in min..max
}

/** What the panel has switched on, by criterion (ADR 0035, 0046). Nothing is switched on at first, so nothing is dimmed. */
data class FilterSelection(val ranges: Map<FilterCriterion, FilterRange> = emptyMap()) {
    val isActive: Boolean get() = ranges.isNotEmpty()
}

/**
 * The outcome of applying a selection: the suburbs that pass every switched-on criterion (null while none is on) and, for each
 * criterion, how many suburbs have no value for it.
 */
data class FilterResult(
    val selection: FilterSelection,
    val passingIds: Set<String>?,
    val total: Int,
    val unknown: Map<FilterCriterion, Int>,
) {
    val matching: Int get() = passingIds?.size ?: total
}

/** Where a criterion's figures come from, as on the card. */
data class FilterSource(val source: String, val asOf: LocalDate?)

/**
 * The values the panel works on, from the one filters response, with where each criterion's come from. A suburb with no value
 * for a switched-on criterion never passes and is counted as unknown instead (ADR 0035).
 */
class FilterData(
    private val values: Map<FilterCriterion, Map<String, Double?>>,
    private val sources: Map<FilterCriterion, FilterSource>,
    private val suburbIds: Set<String>,
    private val names: Map<String, String> = emptyMap(),
) {
    val total: Int get() = suburbIds.size

    /** The range a criterion's sliders span: its lowest and highest value, rounded out to whole steps; null when no suburb has one. */
    fun bounds(criterion: FilterCriterion): FilterRange? {
        val known = values[criterion].orEmpty().values.filterNotNull()
        if (known.isEmpty()) return null
        return FilterRange(floor(known.min() / criterion.step) * criterion.step, ceil(known.max() / criterion.step) * criterion.step)
    }

    fun hasData(criterion: FilterCriterion): Boolean = bounds(criterion) != null
    val hasAnyData: Boolean get() = FilterCriterion.entries.any(::hasData)

    fun source(criterion: FilterCriterion): FilterSource? = sources[criterion]

    fun name(suburbId: String): String = names[suburbId]?.takeIf { it.isNotBlank() } ?: suburbId

    fun unknown(criterion: FilterCriterion): Int = suburbIds.count { values[criterion]?.get(it) == null }

    fun apply(selection: FilterSelection): FilterResult {
        val unknown = FilterCriterion.entries.associateWith(::unknown)
        if (!selection.isActive) return FilterResult(selection, null, total, unknown)
        val passing = suburbIds.filterTo(HashSet()) { id ->
            selection.ranges.all { (criterion, range) -> values[criterion]?.get(id)?.let { it in range } == true }
        }
        return FilterResult(selection, passing, total, unknown)
    }

    companion object {
        private const val PERCENT = 100.0

        fun from(response: SuburbFiltersResponse): FilterData {
            val ids = response.suburbs.map { it.suburbId }.toSet()
            val values = mapOf(
                FilterCriterion.ICSEA to response.suburbs.associate { it.suburbId to it.averageIcsea?.toDouble() },
                FilterCriterion.BEACH to response.suburbs.associate { it.suburbId to it.beachKm },
                FilterCriterion.PARKS to response.suburbs.associate { it.suburbId to it.parkShare?.times(PERCENT) },
                FilterCriterion.RENTED to response.suburbs.associate { it.suburbId to it.rentedShare?.times(PERCENT) },
                FilterCriterion.SEIFA to response.suburbs.associate { it.suburbId to it.seifaDecile?.toDouble() },
            )
            val sources = buildMap {
                put(FilterCriterion.ICSEA, FilterSource(response.icseaSource, response.icseaAsOf))
                response.beach?.let { put(FilterCriterion.BEACH, FilterSource(it.source, it.asOf)) }
                response.parks?.let { put(FilterCriterion.PARKS, FilterSource(it.source, it.asOf)) }
                response.rented?.let { put(FilterCriterion.RENTED, FilterSource(it.source, it.asOf)) }
                response.seifa?.let { put(FilterCriterion.SEIFA, FilterSource(it.source, it.asOf)) }
            }
            return FilterData(values, sources, ids, response.suburbs.associate { it.suburbId to it.name })
        }
    }
}
