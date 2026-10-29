package au.homefinder.server

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.SuburbReport

/**
 * Builds the report for a suburb. Only what the boundary data gives (name, postcode, area) is known so far; every
 * statistic is explicitly unknown, with the source that will supply it, until the pipeline computes it (later PRs).
 */
object SuburbReportFactory {
    const val BOUNDARY_SOURCE: String = "NSW Spatial Services suburb boundary"
    const val NOT_COMPUTED_NOTE: String = "Not computed yet"
    private const val SALES_SOURCE = "NSW Valuer General"
    private const val CADASTRE_SOURCE = "NSW Digital Cadastral Database"
    private const val FLOOD_SOURCE = "NSW Flood Data Portal and council studies"
    private const val LISTING_SOURCE = "Licensed listing text"
    private const val OSM_SOURCE = "OpenStreetMap"
    private const val EDUCATION_SOURCE = "NSW Department of Education"
    private const val TRANSPORT_SOURCE = "Transport for NSW"

    private fun <T> unknown(source: String): Attribute<T> = Attribute.unknown(source, note = NOT_COMPUTED_NOTE)

    fun build(record: SuburbRecord): SuburbReport = SuburbReport(
        suburbId = record.suburbId,
        name = record.name,
        postcode = record.postcode,
        areaKm2 = record.areaKm2
            ?.let { Attribute(it, Confidence.HIGH, BOUNDARY_SOURCE) }
            ?: Attribute.unknown(BOUNDARY_SOURCE, note = NOT_COMPUTED_NOTE),
        medianHousePriceAud = unknown(SALES_SOURCE),
        medianUnitPriceAud = unknown(SALES_SOURCE),
        priceChange12MonthsPercent = unknown(SALES_SOURCE),
        salesCount = unknown(SALES_SOURCE),
        blockSize = unknown(CADASTRE_SOURCE),
        floodShare = unknown(FLOOD_SOURCE),
        brickShare = unknown(LISTING_SOURCE),
        beach = unknown(OSM_SOURCE),
        schools = unknown(EDUCATION_SOURCE),
        parkAreaShare = unknown(OSM_SOURCE),
        shopping = unknown(OSM_SOURCE),
        transport = unknown(TRANSPORT_SOURCE),
    )
}
