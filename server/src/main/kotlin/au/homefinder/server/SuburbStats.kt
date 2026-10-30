package au.homefinder.server

import kotlinx.datetime.LocalDate

/**
 * One suburb's row of `suburb_stats`, built by the pipeline from the Valuer General sales and the cadastre (ADR 0018).
 * A median is null when too few sales (or lots) back it; the counts are always present so the card can say how few.
 * [asOf] is the latest sales file date, null when no sales have been loaded.
 */
data class SuburbStats(
    val asOf: LocalDate?,
    val houseSales: Int,
    val houseMedianAud: Long?,
    val unitSales: Int,
    val unitMedianAud: Long?,
    val housePrevSales: Int,
    val houseChangePercent: Double?,
    val blockLots: Int,
    val blockMedianM2: Double?,
    val blockP10M2: Double?,
    val blockP90M2: Double?,
)
