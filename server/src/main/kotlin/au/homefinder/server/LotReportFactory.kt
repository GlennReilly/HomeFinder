package au.homefinder.server

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.LotReport

/**
 * Builds the basic lot card (ADR 0022): the lot, its suburb, its addresses and its measured area are known; every other
 * factor is an explicit unknown with the source that will supply it, until its pipeline lands.
 */
object LotReportFactory {
    const val NOT_COMPUTED_NOTE: String = SuburbReportFactory.NOT_COMPUTED_NOTE
    const val AREA_SOURCE: String = "NSW Digital Cadastral Database"
    const val AREA_NOTE: String = "Area of the lot polygon, calculated in GDA2020 / MGA zone 56"
    const val STRATA_NOTE: String =
        "This lot is part of a strata plan: the area is the whole parcel's, not one unit's share of it"
    private const val FLOOD_SOURCE = "NSW Flood Data Portal and council studies"
    private const val ASPECT_SOURCE = "NSW cadastre lot, building footprint and road centrelines"
    private const val SLOPE_SOURCE = "Geoscience Australia ELVIS"
    private const val PRICE_SOURCE = "NSW Valuer General"
    private const val LISTING_SOURCE = "Licensed listing text"
    private const val OSM_SOURCE = "OpenStreetMap"
    private const val EDUCATION_SOURCE = "NSW Department of Education"
    private const val TRANSPORT_SOURCE = "Transport for NSW"

    /** Strata and community plans (SP and CP) share one parcel across many titles. */
    private val STRATA_PLAN = Regex("(^|/)(SP|CP)")

    fun isStrata(parcelKey: String?): Boolean = parcelKey != null && STRATA_PLAN.containsMatchIn(parcelKey)

    private fun <T> unknown(source: String): Attribute<T> = Attribute.unknown(source, note = NOT_COMPUTED_NOTE)

    fun build(record: LotRecord): LotReport {
        val strata = isStrata(record.parcelKey)
        return LotReport(
            lotId = record.lotId,
            parcelLabel = record.parcelLabel,
            suburbId = record.suburbId,
            suburbName = record.suburbName,
            postcode = record.postcode,
            addresses = record.addresses,
            blockAreaM2 = Attribute(
                record.areaM2, if (strata) Confidence.MEDIUM else Confidence.HIGH, AREA_SOURCE, null,
                if (strata) "$AREA_NOTE. $STRATA_NOTE" else AREA_NOTE,
            ),
            wallMaterial = unknown(LISTING_SOURCE),
            backyardAspect = unknown(ASPECT_SOURCE),
            flood = unknown(FLOOD_SOURCE),
            slope = unknown(SLOPE_SOURCE),
            estimatedPriceAud = unknown(PRICE_SOURCE),
            beach = unknown(OSM_SOURCE),
            schools = unknown(EDUCATION_SOURCE),
            parks = unknown(OSM_SOURCE),
            shopping = unknown(OSM_SOURCE),
            transport = unknown(TRANSPORT_SOURCE),
        )
    }
}
