package au.homefinder.server

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.BlockSizeSpread
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.SuburbReport

/**
 * Builds the report for a suburb. Name, postcode and area come from the boundary data; the sales and block-size
 * statistics come from `suburb_stats` when the pipeline has built it (ADR 0018, 0021). Everything else is explicitly
 * unknown, with the source that will supply it, until a later PR computes it.
 */
object SuburbReportFactory {
    const val BOUNDARY_SOURCE: String = "NSW Spatial Services suburb boundary"
    const val NOT_COMPUTED_NOTE: String = "Not computed yet"
    const val SALES_SOURCE: String = "NSW Valuer General Property Sales Information"
    const val SALES_NOT_LOADED_NOTE: String = "Sales data not loaded yet"
    const val CADASTRE_SOURCE: String = "NSW Digital Cadastral Database"
    private const val FLOOD_SOURCE = "NSW Flood Data Portal and council studies"
    private const val LISTING_SOURCE = "Licensed listing text"
    private const val OSM_SOURCE = "OpenStreetMap"
    private const val EDUCATION_SOURCE = "NSW Department of Education"
    private const val TRANSPORT_SOURCE = "Transport for NSW"

    private fun <T> unknown(source: String): Attribute<T> = Attribute.unknown(source, note = NOT_COMPUTED_NOTE)

    /** Fewer samples than this is low confidence, fewer than [MEDIUM_CONFIDENCE_BELOW] medium, otherwise high. */
    const val LOW_CONFIDENCE_BELOW: Int = 15
    const val MEDIUM_CONFIDENCE_BELOW: Int = 50

    fun confidenceForSamples(count: Int): Confidence = when {
        count < LOW_CONFIDENCE_BELOW -> Confidence.LOW
        count < MEDIUM_CONFIDENCE_BELOW -> Confidence.MEDIUM
        else -> Confidence.HIGH
    }

    fun build(record: SuburbRecord, stats: SuburbStats? = null): SuburbReport = SuburbReport(
        suburbId = record.suburbId,
        name = record.name,
        postcode = record.postcode,
        areaKm2 = record.areaKm2
            ?.let { Attribute(it, Confidence.HIGH, BOUNDARY_SOURCE) }
            ?: Attribute.unknown(BOUNDARY_SOURCE, note = NOT_COMPUTED_NOTE),
        medianHousePriceAud = median(stats, "house", stats?.houseSales, stats?.houseMedianAud),
        medianUnitPriceAud = median(stats, "unit", stats?.unitSales, stats?.unitMedianAud),
        priceChange12MonthsPercent = priceChange(stats),
        salesCount = salesCount(stats),
        blockSize = blockSize(stats),
        floodShare = unknown(FLOOD_SOURCE),
        brickShare = unknown(LISTING_SOURCE),
        beach = unknown(OSM_SOURCE),
        schools = unknown(EDUCATION_SOURCE),
        parkAreaShare = unknown(OSM_SOURCE),
        shopping = unknown(OSM_SOURCE),
        transport = unknown(TRANSPORT_SOURCE),
    )

    private fun <T> salesNotLoaded(): Attribute<T> = Attribute.unknown(SALES_SOURCE, note = SALES_NOT_LOADED_NOTE)

    /** True when sales have been loaded; without a latest file date there is nothing to say about prices. */
    private fun SuburbStats?.hasSales(): Boolean = this?.asOf != null

    private fun median(stats: SuburbStats?, kind: String, sales: Int?, median: Long?): Attribute<Long> {
        if (stats == null || !stats.hasSales() || sales == null) return salesNotLoaded()
        return if (median != null) {
            Attribute(median, confidenceForSamples(sales), SALES_SOURCE, stats.asOf, "Based on $sales $kind sales in the 12 months to ${stats.asOf}")
        } else {
            val note = if (sales == 0) "No $kind sales in the 12 months to ${stats.asOf}" else "Only $sales $kind sales in the 12 months to ${stats.asOf}, too few for a median"
            Attribute.unknown(SALES_SOURCE, stats.asOf, note)
        }
    }

    private fun priceChange(stats: SuburbStats?): Attribute<Double> {
        if (stats == null || !stats.hasSales()) return salesNotLoaded()
        val change = stats.houseChangePercent
        val basis = "${stats.houseSales} house sales in the 12 months to ${stats.asOf} and ${stats.housePrevSales} in the 12 months before"
        return if (change != null) {
            Attribute(change, confidenceForSamples(minOf(stats.houseSales, stats.housePrevSales)), SALES_SOURCE, stats.asOf, "House median price, from $basis")
        } else {
            Attribute.unknown(SALES_SOURCE, stats.asOf, "Too few house sales to compare two 12-month periods ($basis)")
        }
    }

    private fun salesCount(stats: SuburbStats?): Attribute<Int> {
        if (stats == null || !stats.hasSales()) return salesNotLoaded()
        val note = "${stats.houseSales} house and ${stats.unitSales} unit sales in the 12 months to ${stats.asOf}. " +
            "Sales reach the Valuer General weeks late, so the latest weeks are thin."
        return Attribute(stats.houseSales + stats.unitSales, Confidence.MEDIUM, SALES_SOURCE, stats.asOf, note)
    }

    private fun blockSize(stats: SuburbStats?): Attribute<BlockSizeSpread> {
        val median = stats?.blockMedianM2
        val p10 = stats?.blockP10M2
        val p90 = stats?.blockP90M2
        if (stats == null || stats.blockLots == 0) return unknown(CADASTRE_SOURCE)
        if (median == null || p10 == null || p90 == null) {
            return Attribute.unknown(CADASTRE_SOURCE, note = "Only ${stats.blockLots} lots with an address, too few for a spread")
        }
        return Attribute(
            BlockSizeSpread(median, p10, p90), confidenceForSamples(stats.blockLots), CADASTRE_SOURCE, null,
            "Lots with an address, not strata, of at least 100 m² (${stats.blockLots} lots)",
        )
    }
}
