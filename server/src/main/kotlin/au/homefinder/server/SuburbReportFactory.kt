package au.homefinder.server

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.BlockSizeSpread
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.HomelyRating
import au.homefinder.shared.model.School
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolSector
import au.homefinder.shared.model.SuburbSchools
import au.homefinder.shared.model.SuburbBeachDistance
import au.homefinder.shared.model.SuburbShopping
import kotlin.math.roundToInt
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

    fun build(
        record: SuburbRecord,
        stats: SuburbStats? = null,
        rating: StoredRating? = null,
        amenity: SuburbAmenity? = null,
        schools: SuburbSchoolData? = null,
    ): SuburbReport = SuburbReport(
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
        beach = beach(amenity),
        schools = schools(schools),
        parkAreaShare = parks(amenity),
        shopping = shopping(amenity),
        transport = unknown(TRANSPORT_SOURCE),
        homelyRating = homelyRating(rating),
    )

    const val HOMELY_NOT_ENTERED_NOTE: String = "Not entered"

    /**
     * A score you typed in. It is an opinion of Homely's users that you copied by hand, so its confidence follows how many
     * reviews you noted (none noted counts as low) and its note says where it came from.
     */
    private fun homelyRating(stored: StoredRating?): Attribute<HomelyRating> {
        if (stored == null) return Attribute.unknown(HomelyRating.SOURCE, note = HOMELY_NOT_ENTERED_NOTE)
        val reviews = stored.rating.reviewCount
        return Attribute(
            stored.rating, confidenceForSamples(reviews ?: 0), HomelyRating.SOURCE, stored.ratedOn,
            "Homely users' opinion, typed in by you; not fetched from Homely" + (reviews?.let { " ($it reviews)" } ?: ""),
        )
    }

    const val SCHOOLS_SOURCE: String = "NSW Department of Education public schools master dataset (CC BY); ICSEA from ACARA"
    const val SCHOOLS_NOT_LOADED_NOTE: String = "School data not loaded yet"
    const val SCHOOLS_NOTE: String =
        "NSW public schools only (private and Catholic schools are not in this data). ICSEA measures how socio-educationally advantaged " +
            "a school's students are (national average 1000), not how good the school is. Catchments are not included yet."

    /** What the dataset calls a kind of school, as the model's three levels; anything else (support, special) counts as combined. */
    fun schoolLevel(label: String?): SchoolLevel = when {
        label == null -> SchoolLevel.COMBINED
        label.contains("Primary", ignoreCase = true) || label.contains("Infants", ignoreCase = true) -> SchoolLevel.PRIMARY
        label.contains("Secondary", ignoreCase = true) || label.contains("High", ignoreCase = true) -> SchoolLevel.HIGH
        else -> SchoolLevel.COMBINED
    }

    /** Only an http or https address is kept, so a link on the card can never run a script. */
    fun safeWebsite(address: String?): String? = address?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

    private fun schools(data: SuburbSchoolData?): Attribute<SuburbSchools> {
        if (data == null) return Attribute.unknown(SCHOOLS_SOURCE, note = SCHOOLS_NOT_LOADED_NOTE)
        val schools = data.schools.map {
            School(
                id = it.code.toString(), name = it.name, sector = SchoolSector.PUBLIC, level = schoolLevel(it.levelLabel), icsea = it.icsea,
                enrolments = it.enrolments, website = safeWebsite(it.website), levelLabel = it.levelLabel,
            )
        }
        val average = schools.mapNotNull { it.icsea }.takeIf { it.isNotEmpty() }?.let { it.average().roundToInt() }
        return Attribute(SuburbSchools(schools, emptyList(), average), Confidence.HIGH, SCHOOLS_SOURCE, data.extractedOn, SCHOOLS_NOTE)
    }

    private fun <T> osmNotLoaded(): Attribute<T> = Attribute.unknown(LotReportFactory.OSM_SOURCE, note = LotReportFactory.OSM_NOT_LOADED_NOTE)

    /** From the suburb's centre and from its nearest edge, in a straight line, to the nearest ocean beach on the mapped coastline. */
    private fun beach(amenity: SuburbAmenity?): Attribute<SuburbBeachDistance> {
        if (amenity == null) return osmNotLoaded()
        val centre = amenity.beachFromCentreM
        val edge = amenity.beachFromEdgeM
        if (centre == null || edge == null) {
            return Attribute.unknown(LotReportFactory.OSM_SOURCE, amenity.osmAsOf, "No ocean beach found in the OpenStreetMap data loaded")
        }
        val name = amenity.beachName?.let { " ($it)" }.orEmpty()
        return Attribute(
            SuburbBeachDistance(centre.roundToInt(), edge.roundToInt()), Confidence.MEDIUM, LotReportFactory.OSM_SOURCE, amenity.osmAsOf,
            "Straight line to the nearest beach on the mapped coastline$name, from the suburb's centre and from its nearest edge",
        )
    }

    private fun parks(amenity: SuburbAmenity?): Attribute<Double> {
        if (amenity == null) return osmNotLoaded()
        val share = amenity.parkShare
            ?: return Attribute.unknown(LotReportFactory.OSM_SOURCE, amenity.osmAsOf, "The suburb has no area to measure parks against")
        return Attribute(
            share, Confidence.MEDIUM, LotReportFactory.OSM_SOURCE, amenity.osmAsOf,
            "Parks of at least 0.5 ha as a share of the suburb's area; OpenStreetMap may be incomplete",
        )
    }

    /** None found at all is shown as zero with low confidence: in OpenStreetMap, no shop usually means no shop mapped. */
    private fun shopping(amenity: SuburbAmenity?): Attribute<SuburbShopping> {
        if (amenity == null) return osmNotLoaded()
        val none = amenity.shoppingCentres == 0 && amenity.supermarkets == 0
        return Attribute(
            SuburbShopping(amenity.shoppingCentres, amenity.supermarkets), if (none) Confidence.LOW else Confidence.MEDIUM,
            LotReportFactory.OSM_SOURCE, amenity.osmAsOf,
            "Inside the suburb or within 2 km of it" + if (none) "; none are mapped in OpenStreetMap, which may be incomplete" else "; OpenStreetMap may be incomplete",
        )
    }

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
