package au.homefinder.app.filter

import au.homefinder.app.selection.Formats
import au.homefinder.shared.model.NaplanRules
import au.homefinder.shared.model.SuburbFiltersResponse
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Crime rates have a long tail (a few nightlife and retail suburbs run at several times the median), so their sliders span the lowest 90% of suburbs. */
const val CRIME_CAP_PERCENTILE: Double = 0.90

/** What the label says when the slider is on its last position and applies no limit (ADR 0055). */
const val NO_LIMIT_TEXT: String = "No limit"

/** What the NAPLAN filter says until a figure has been typed for a zoned school (ADR 0074). */
const val NAPLAN_EMPTY_TEXT: String = "No NAPLAN figures typed yet"

/** Which side of its limit a suburb must be on to pass a criterion (ADR 0052). */
enum class Direction(val word: String) {
    AT_LEAST("At least"),
    AT_MOST("At most");

    fun passes(value: Double, limit: Double): Boolean = if (this == AT_LEAST) value >= limit else value <= limit
}

/** One thing the panel can filter suburbs on (ADR 0035, 0046, 0052). Every value is a number in the unit shown beside its slider. */
enum class FilterCriterion(
    /** Part of the element ids (`filter-<key>-on`, `-limit`, `-limit-value`), so the page can find the pieces. */
    val key: String,
    val label: String,
    /** What a value ends with: nothing, " km", "%" or " per 1,000". */
    val unit: String,
    /** The one slider's direction: the side of the limit that passes (a high ICSEA is better, a short distance to the beach is better). */
    val direction: Direction,
    /** The slider step, also the grid the slider's ends are rounded out to. */
    val step: Double,
    /** What a suburb with no value lacks, for "N of M suburbs have no <this> and never match". */
    val missing: String,
    /** A line under the slider that explains what the number is and where it falls short; null when the label says it all. */
    val help: String?,
    /** What to tell the user when this criterion's data has not been loaded. */
    val notLoadedText: String,
    /**
     * For an "at most" criterion with a long tail of outliers: the share of suburbs (0 to 1, nearest rank) the slider spans, so a few extreme
     * suburbs do not squeeze the rest into one corner. The slider then has one more position past it, "No limit" (ADR 0055). Null: no cap.
     */
    val capPercentile: Double? = null,
    /**
     * What the panel says, beside a slider that cannot be switched on, when the data is there but no suburb has a value yet (the NAPLAN figures are
     * yours to type, ADR 0074). Null: such a criterion shows [notLoadedText] like one whose data is missing.
     */
    val emptyText: String? = null,
    /** Whether the help line ends with how many suburbs have a value ("N of M suburbs have at least one"), for a criterion with partial coverage. */
    val countsCoverage: Boolean = false,
    /** A longer explanation for the panel's Details section (ADR 0072), for a criterion whose limits need more than the help line. */
    val detailsNote: String? = null,
    /** Whether a value is a difference that always shows its sign ("+12", "-30", "0"), like a NAPLAN figure. */
    val signed: Boolean = false,
) {
    ICSEA(
        "icsea", "School ICSEA (average of public schools in the suburb)", "", Direction.AT_LEAST, 10.0, "ICSEA",
        "ICSEA measures the socio-educational advantage of a school's students (1,000 is the average). It is not a measure of school quality.",
        "School data has not been loaded yet, so there is nothing to filter on.",
    ),
    BEACH(
        "beach", "Distance to the beach (from the suburb's centre)", " km", Direction.AT_MOST, 1.0, "beach distance",
        "A straight line from the suburb's centre to the nearest ocean beach on the mapped coastline, not a route.",
        "OpenStreetMap data has not been loaded yet, so there is no beach distance to filter on.",
    ),
    PARKS(
        "parks", "Park share of the suburb", "%", Direction.AT_LEAST, 1.0, "mapped park",
        "Parks of at least 0.5 ha as a share of the suburb's area. OpenStreetMap may be incomplete, and a suburb with no park mapped has no value.",
        "OpenStreetMap data has not been loaded yet, so there is no park share to filter on.",
    ),
    RENTED(
        "rented", "Homes rented", "%", Direction.AT_MOST, 1.0, "Census figure",
        "The share of occupied homes that were rented on Census night 2021. A suburb with few homes is only roughly right.",
        "Census data has not been loaded yet, so there is nothing to filter on.",
    ),
    SEIFA(
        "seifa", "Socio-economic advantage (SEIFA decile, 10 = most advantaged)", "", Direction.AT_LEAST, 1.0, "SEIFA decile",
        "From the ABS SEIFA 2021: it describes the residents of the suburb as a group, ranked across Australia from decile 1 (most disadvantaged) to 10 (most advantaged).",
        "SEIFA data has not been loaded yet, so there is nothing to filter on.",
    ),
    VIOLENT(
        "violent", "Violent crime (incidents per 1,000 residents in 24 months)", " per 1,000", Direction.AT_MOST, 1.0, "violent crime rate",
        "Assaults, robberies and sexual offences recorded by the NSW Bureau of Crime Statistics and Research. Crime is counted where it happens, so a suburb with a shopping or business district looks worse than its residents warrant. Suburbs under 500 residents have no rate.",
        "Crime data (and the SEIFA population it needs) has not been loaded yet, so there is nothing to filter on.",
        capPercentile = CRIME_CAP_PERCENTILE,
    ),
    PROPERTY(
        "property", "Break-ins, car and home theft (incidents per 1,000 residents in 24 months)", " per 1,000", Direction.AT_MOST, 1.0, "property crime rate",
        "Break-ins, vehicle theft, stealing from cars and homes and malicious damage, recorded by the NSW Bureau of Crime Statistics and Research. Counted where it happens, and reflects reporting and policing. Suburbs under 500 residents have no rate.",
        "Crime data (and the SEIFA population it needs) has not been loaded yet, so there is nothing to filter on.",
        capPercentile = CRIME_CAP_PERCENTILE,
    ),
    ZONED(
        "zoned", "Zoned schools' ICSEA (average of the schools whose catchments cover the suburb)", "", Direction.AT_LEAST, 10.0, "zoned-school ICSEA",
        "The ICSEA of the public primary and high schools whose catchments cover at least a quarter of the suburb's lots, averaged and weighted by how much of the suburb each covers. " +
            "Catchments change, and ICSEA is not a measure of school quality.",
        "School catchments (and the schools data they are matched to) have not been loaded yet, so there is nothing to filter on.",
    ),
    NAPLAN(
        "naplan", "Zoned schools' NAPLAN (vs national)", "", Direction.AT_LEAST, 1.0, "NAPLAN figure for a zoned school",
        "Average of the NAPLAN figures you typed for the zoned schools, weighted by how much of the suburb each zone covers",
        "School catchments (and the schools data they are matched to) have not been loaded yet, so there is no NAPLAN figure to filter on.",
        emptyText = NAPLAN_EMPTY_TEXT,
        countsCoverage = true,
        detailsNote = "Zoned schools' NAPLAN: your own figures, typed in one school at a time on the cards (a school's difference from the national " +
            "average in points, from My School). A suburb's value is the average over the zoned schools the Suburb Card lists that have a figure, " +
            "weighted by how much of the suburb each zone covers; schools you have not typed a figure for are left out, so a value may rest on one " +
            "school. A suburb with no figure for any zoned school has no value and never matches while this filter is on. " +
            "On the map, from zoom 12, a dark-blue diamond with this value (for example +12) sits under each suburb that has one; click it to open that suburb's Schools.",
        signed = true,
    );

    fun format(value: Double): String = (if (signed) NaplanRules.signed(value.roundToInt()) else Formats.decimal(value, 0)) + unit

    /** The end of the slider that every suburb with a value passes, where it starts: the lowest value for "at least", the highest for "at most". */
    fun startLimit(bounds: FilterRange): Double = if (direction == Direction.AT_LEAST) bounds.min else bounds.max
}

/** An inclusive range of a criterion's values. */
data class FilterRange(val min: Double, val max: Double) {
    init {
        require(min <= max) { "min $min is above max $max" }
    }

    operator fun contains(value: Double): Boolean = value in min..max
}

/** What the panel has switched on: each criterion's limit, read in its own [FilterCriterion.direction] (ADR 0052). Nothing is on at first, so nothing is dimmed. */
data class FilterSelection(val limits: Map<FilterCriterion, Double> = emptyMap()) {
    val isActive: Boolean get() = limits.isNotEmpty()

    /**
     * This selection carried over to freshly loaded values (after a NAPLAN figure is saved, ADR 0074): a criterion that now has no values is switched
     * off, and a limit outside the new range is moved to its nearest end.
     */
    fun carriedTo(data: FilterData): FilterSelection = FilterSelection(
        limits.mapNotNull { (criterion, limit) -> data.bounds(criterion)?.let { criterion to limit.coerceIn(it.min, it.max) } }.toMap(),
    )
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

    /**
     * The range a criterion's slider spans: its lowest and highest value, rounded out to whole steps; null when no suburb has one. A capped
     * criterion with outliers spans its lowest value to its cap, plus one step: the last position is "No limit" (ADR 0055).
     */
    fun bounds(criterion: FilterCriterion): FilterRange? {
        val known = values[criterion].orEmpty().values.filterNotNull()
        if (known.isEmpty()) return null
        val low = floor(known.min() / criterion.step) * criterion.step
        val cap = cap(criterion)
        return if (cap != null) FilterRange(low, cap + criterion.step) else FilterRange(low, ceil(known.max() / criterion.step) * criterion.step)
    }

    /**
     * The highest limit a capped "at most" slider offers before "No limit": the value at [FilterCriterion.capPercentile] (nearest rank), rounded up
     * to a step. Null when the criterion is not capped, has no values, or no suburb lies above the cap (then the slider needs no extra position).
     */
    fun cap(criterion: FilterCriterion): Double? {
        val share = criterion.capPercentile ?: return null
        if (criterion.direction != Direction.AT_MOST) return null
        val known = values[criterion].orEmpty().values.filterNotNull().sorted()
        if (known.isEmpty()) return null
        val rank = ceil(share * known.size).toInt().coerceIn(1, known.size)
        val cap = ceil(known[rank - 1] / criterion.step) * criterion.step
        return cap.takeIf { it < ceil(known.last() / criterion.step) * criterion.step }
    }

    /** The slider position that means "No limit" (one step past the cap), or null when the criterion has none. */
    fun noLimitPosition(criterion: FilterCriterion): Double? = cap(criterion)?.plus(criterion.step)

    private fun isNoLimit(criterion: FilterCriterion, limit: Double): Boolean = noLimitPosition(criterion)?.let { limit >= it } == true

    /** The words before the number on a slider's label: "At most ", or nothing at "No limit". */
    fun limitPrefix(criterion: FilterCriterion, limit: Double): String =
        if (isEmpty(criterion) || isNoLimit(criterion, limit)) "" else "${criterion.direction.word} "

    /** The number on a slider's label: "40 per 1,000", "No limit" at the last position of a capped slider, or the empty text when there are no values. */
    fun limitText(criterion: FilterCriterion, limit: Double): String = when {
        isEmpty(criterion) -> criterion.emptyText.orEmpty()
        isNoLimit(criterion, limit) -> NO_LIMIT_TEXT
        else -> criterion.format(limit)
    }

    fun hasData(criterion: FilterCriterion): Boolean = bounds(criterion) != null
    val hasAnyData: Boolean get() = FilterCriterion.entries.any(::hasData)

    fun source(criterion: FilterCriterion): FilterSource? = sources[criterion]

    /** True when the criterion's data is there but no suburb has a value yet, so its slider is shown disabled with [FilterCriterion.emptyText]. */
    fun isEmpty(criterion: FilterCriterion): Boolean = criterion.emptyText != null && sources[criterion] != null && !hasData(criterion)

    /** How many suburbs have a value for the criterion. */
    fun known(criterion: FilterCriterion): Int = total - unknown(criterion)

    /** The line under the slider: the criterion's help, ending with its coverage when it [FilterCriterion.countsCoverage]. */
    fun helpText(criterion: FilterCriterion): String? {
        val help = criterion.help ?: return null
        return if (criterion.countsCoverage) "$help; ${known(criterion)} of $total suburbs have at least one." else help
    }

    fun name(suburbId: String): String = names[suburbId]?.takeIf { it.isNotBlank() } ?: suburbId

    fun unknown(criterion: FilterCriterion): Int = suburbIds.count { values[criterion]?.get(it) == null }

    fun apply(selection: FilterSelection): FilterResult {
        val unknown = FilterCriterion.entries.associateWith(::unknown)
        if (!selection.isActive) return FilterResult(selection, null, total, unknown)
        val passing = suburbIds.filterTo(HashSet()) { id ->
            selection.limits.all { (criterion, limit) ->
                values[criterion]?.get(id)?.let { isNoLimit(criterion, limit) || criterion.direction.passes(it, limit) } == true
            }
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
                FilterCriterion.VIOLENT to response.suburbs.associate { it.suburbId to it.violentPer1000 },
                FilterCriterion.PROPERTY to response.suburbs.associate { it.suburbId to it.propertyPer1000 },
                FilterCriterion.ZONED to response.suburbs.associate { it.suburbId to it.zonedIcsea?.toDouble() },
                FilterCriterion.NAPLAN to response.suburbs.associate { it.suburbId to it.zonedNaplan?.toDouble() },
            )
            val sources = buildMap {
                put(FilterCriterion.ICSEA, FilterSource(response.icseaSource, response.icseaAsOf))
                response.beach?.let { put(FilterCriterion.BEACH, FilterSource(it.source, it.asOf)) }
                response.parks?.let { put(FilterCriterion.PARKS, FilterSource(it.source, it.asOf)) }
                response.rented?.let { put(FilterCriterion.RENTED, FilterSource(it.source, it.asOf)) }
                response.seifa?.let { put(FilterCriterion.SEIFA, FilterSource(it.source, it.asOf)) }
                response.zoned?.let { put(FilterCriterion.ZONED, FilterSource(it.source, it.asOf)) }
                response.naplan?.let { put(FilterCriterion.NAPLAN, FilterSource(it.source, it.asOf)) }
                response.crime?.let {
                    put(FilterCriterion.VIOLENT, FilterSource(it.source, it.asOf))
                    put(FilterCriterion.PROPERTY, FilterSource(it.source, it.asOf))
                }
            }
            return FilterData(values, sources, ids, response.suburbs.associate { it.suburbId to it.name })
        }
    }
}
