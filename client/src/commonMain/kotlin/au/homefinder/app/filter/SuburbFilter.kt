package au.homefinder.app.filter

import au.homefinder.shared.model.SuburbFiltersResponse
import kotlinx.datetime.LocalDate

/** An inclusive range of average ICSEA. */
data class IcseaRange(val min: Int, val max: Int) {
    init {
        require(min <= max) { "min $min is above max $max" }
    }

    operator fun contains(value: Int): Boolean = value in min..max
}

/** What the panel has switched on (ADR 0035). Nothing is switched on at first, so nothing is dimmed. */
data class FilterSelection(val icsea: IcseaRange? = null) {
    val isActive: Boolean get() = icsea != null
}

/** The outcome of applying a selection: the suburbs that pass (null while no criterion is on) and the counts the panel shows. */
data class FilterResult(
    val selection: FilterSelection,
    val passingIds: Set<String>?,
    val total: Int,
    val unknownIcsea: Int,
) {
    val matching: Int get() = passingIds?.size ?: total
}

/**
 * The values the panel works on, from the one filters response, with where they come from. A suburb with no ICSEA never
 * passes an ICSEA filter and is counted as unknown instead (ADR 0035).
 */
class FilterData(private val icseaBySuburb: Map<String, Int?>, val source: String, val asOf: LocalDate?) {
    /** The range the sliders span: the lowest and highest average ICSEA, widened to whole steps; null when none is known. */
    val icseaBounds: IcseaRange? = icseaBySuburb.values.filterNotNull().let { known ->
        if (known.isEmpty()) null else IcseaRange(floorStep(known.min()), ceilStep(known.max()))
    }

    val hasIcsea: Boolean get() = icseaBounds != null
    val total: Int get() = icseaBySuburb.size

    fun apply(selection: FilterSelection): FilterResult {
        val unknown = icseaBySuburb.values.count { it == null }
        val range = selection.icsea ?: return FilterResult(selection, null, total, unknown)
        val passing = icseaBySuburb.filterValues { it != null && it in range }.keys
        return FilterResult(selection, passing, total, unknown)
    }

    companion object {
        const val ICSEA_STEP: Int = 10

        private fun floorStep(value: Int): Int = value.floorDiv(ICSEA_STEP) * ICSEA_STEP
        private fun ceilStep(value: Int): Int = -(-value).floorDiv(ICSEA_STEP) * ICSEA_STEP

        fun from(response: SuburbFiltersResponse): FilterData =
            FilterData(response.suburbs.associate { it.suburbId to it.averageIcsea }, response.icseaSource, response.icseaAsOf)
    }
}
