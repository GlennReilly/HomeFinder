package au.homefinder.server

import kotlin.math.roundToInt

/**
 * One school zone's share of a suburb with the NAPLAN figure you typed for its school (ADR 0068, 0069), or null when you have not typed one.
 * [level] is the catchment level ("PRIMARY", "HIGH"); [lotShare] is from 0 to 1.
 */
data class ZoneNaplanShare(val schoolCode: Int, val level: String, val lotShare: Double, val score: Int?)

/**
 * A suburb's value for the "Zoned schools' NAPLAN" filter (ADR 0074): the zoned schools the Suburb Card lists ([SuburbReportFactory.listed]), keeping
 * only those with a figure, averaged and weighted by the share of the suburb each zone covers. Recomputed on every filters request, so a figure you save
 * counts at once.
 */
object ZonedNaplan {
    /** The weighted average, rounded to the nearest whole point (halves upwards), or null when no listed school has a figure. */
    fun average(shares: List<ZoneNaplanShare>): Int? {
        val counted = SuburbReportFactory.listed(shares, { it.level }, { it.lotShare }, { it.schoolCode }).filter { it.score != null && it.lotShare > 0.0 }
        if (counted.isEmpty()) return null
        val weight = counted.sumOf { it.lotShare }
        return (counted.sumOf { it.lotShare * checkNotNull(it.score) } / weight).roundToInt()
    }

    /** Every suburb's average, by suburb id; a suburb whose listed schools have no figure is left out (unknown). */
    fun bySuburb(shares: Map<String, List<ZoneNaplanShare>>): Map<String, Int> =
        shares.mapNotNull { (suburbId, zones) -> average(zones)?.let { suburbId to it } }.toMap()
}
