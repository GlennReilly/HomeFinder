package au.homefinder.server

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.BlockSizeSpread
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.CrimeRate
import au.homefinder.shared.model.DwellingMix
import au.homefinder.shared.model.SocioEconomic
import au.homefinder.shared.model.SuburbZone
import au.homefinder.shared.model.SuburbZonedSchools
import au.homefinder.shared.model.HomelyRating
import au.homefinder.shared.model.NaplanScore
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
        census: SuburbCensus? = null,
        seifa: SuburbSeifa? = null,
        crime: SuburbCrime? = null,
        zones: SuburbZoneData? = null,
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
        rentedShare = rentedShare(census),
        dwellingMix = dwellingMix(census),
        socioEconomic = socioEconomic(seifa),
        disadvantage = seifaDecile(seifa, seifa?.irsdDecile, "Disadvantage only: how many households have low income, low qualifications, unemployment or overcrowding (decile 10 is the least disadvantaged)."),
        economicResources = seifaDecile(seifa, seifa?.ierDecile, "Income, housing costs and assets of the suburb's households (decile 10 has the most economic resources)."),
        educationOccupation = seifaDecile(seifa, seifa?.ieoDecile, "Qualifications and skilled work of the suburb's residents (decile 10 is the highest)."),
        violentCrime = crimeRate(crime, crime?.violent),
        propertyCrime = crimeRate(crime, crime?.property),
        zonedSchools = zonedSchools(zones),
    )

    const val CENSUS_SOURCE: String = "ABS 2021 Census of Population and Housing (CC BY 4.0)"
    const val CENSUS_NOT_LOADED_NOTE: String = "Census data not loaded yet"
    const val CENSUS_ATTRIBUTION: String = "Based on Australian Bureau of Statistics data"
    private const val CENSUS_YEAR = 2021
    private const val CENSUS_NIGHT_MONTH = 8
    private const val CENSUS_NIGHT_DAY = 10
    /** Census night, the date the 2021 Census figures describe. */
    val CENSUS_DATE: kotlinx.datetime.LocalDate = kotlinx.datetime.LocalDate(CENSUS_YEAR, CENSUS_NIGHT_MONTH, CENSUS_NIGHT_DAY)
    private const val SMALL_COUNT_NOTE = "The ABS randomly adjusts small counts, so a suburb with few homes is only roughly right"

    /** Confidence follows how many dwellings the share rests on, as for sales (a share of a few homes is rough). */
    const val SEIFA_SOURCE: String = "ABS Socio-Economic Indexes for Areas (SEIFA) 2021 (CC BY 4.0)"
    const val SEIFA_NOT_LOADED_NOTE: String = "SEIFA data not loaded yet"
    const val SEIFA_NO_SCORE_NOTE: String = "The ABS gives no SEIFA score to this suburb (too few residents)"

    /** Under this many residents a suburb's decile rests on very little (the ABS flags such areas), and under [SEIFA_HIGH_FROM] it is moderately firm. */
    const val SEIFA_LOW_BELOW: Int = 200
    const val SEIFA_HIGH_FROM: Int = 1_000

    const val CRIME_SOURCE: String = "NSW Bureau of Crime Statistics and Research (BOCSAR), recorded criminal incidents (CC BY)"
    const val CRIME_NOT_LOADED_NOTE: String = "Crime data not loaded yet"
    const val CRIME_MONTHS: Int = 24

    /** Under this many residents the rate is shaky (also where the filter stops using it); under [CRIME_HIGH_FROM] it is moderately firm. */
    const val CRIME_LOW_BELOW: Int = 500
    const val CRIME_HIGH_FROM: Int = 2_000
    private const val PER_THOUSAND = 1000.0
    private const val CRIME_CAVEAT =
        "Crime is counted where it happens, so a suburb with a big shopping centre or business district looks worse than its residents warrant, " +
            "and recorded crime also reflects reporting and policing. Based on NSW Bureau of Crime Statistics and Research data"

    const val ZONED_NOT_LOADED_NOTE: String = "School catchments not loaded yet"
    const val ZONED_NONE_NOTE: String = "No school catchment covers at least 5% of this suburb's lots"
    const val ZONED_FILTER_SOURCE: String =
        "NSW Department of Education school catchments and public schools master dataset (CC BY 3.0 Australia); ICSEA from ACARA"

    /** Where the NAPLAN filter's figures come from (ADR 0074): your own, typed in, over the zones from the catchments data. */
    const val NAPLAN_FILTER_SOURCE: String =
        "${NaplanScore.SOURCE} Zones from NSW Department of Education school catchments (CC BY 3.0 Australia)"

    /** A zone covering at least this share of a suburb's lots is listed on the card and counted in the filter (ADR 0062). */
    const val ZONE_LISTED_MIN_SHARE: Double = 0.25
    private const val ZONED_NOTE =
        "The share is how many of the suburb's lots lie inside the zone, so a suburb can be split between schools. Catchments change, being in a zone does not " +
            "guarantee a place, and out-of-area enrolment is common: check with the school. Private and Catholic schools are not in this data. " +
            "Based on NSW Department of Education data"

    /**
     * The zones to list: per level (primary, then high) every zone covering at least [ZONE_LISTED_MIN_SHARE] of the suburb, largest first; a level with none that
     * big still lists its biggest zone, so a suburb split three ways is not left blank.
     */
    fun listedZones(shares: List<SuburbZoneShare>): List<SuburbZoneShare> = listed(shares, { it.zone.level }, { it.lotShare }, { it.zone.schoolCode })

    /** The catchment levels the card lists, in its order. */
    val LISTED_LEVELS: List<String> = listOf("PRIMARY", "HIGH")

    /**
     * The rule behind [listedZones] for any kind of zone share, so the NAPLAN filter (ADR 0074) averages exactly the schools the card lists: per level
     * every item covering at least [ZONE_LISTED_MIN_SHARE], largest first (ties by school code), else the level's biggest one.
     */
    fun <T> listed(items: List<T>, level: (T) -> String, share: (T) -> Double, code: (T) -> Int): List<T> =
        LISTED_LEVELS.flatMap { wanted ->
            val atLevel = items.filter { level(it) == wanted }.sortedWith(compareByDescending<T> { share(it) }.thenBy { code(it) })
            val big = atLevel.filter { share(it) >= ZONE_LISTED_MIN_SHARE }
            big.ifEmpty { atLevel.take(1) }
        }

    private fun zonedSchools(data: SuburbZoneData?): Attribute<SuburbZonedSchools> {
        if (data == null) return Attribute.unknown(LotReportFactory.CATCHMENTS_SOURCE, note = ZONED_NOT_LOADED_NOTE)
        val listed = listedZones(data.shares)
        if (listed.isEmpty()) return Attribute.unknown(LotReportFactory.CATCHMENTS_SOURCE, note = ZONED_NONE_NOTE)
        val year = listed.firstNotNullOfOrNull { it.zone.enrolmentYear }
        val zones = listed.map { SuburbZone(LotReportFactory.zonedSchool(it.zone), it.lotShare.coerceIn(0.0, 1.0)) }
        val yearNote = year?.let { " The zones are for $it enrolments." }.orEmpty()
        return Attribute(SuburbZonedSchools(zones, year), Confidence.MEDIUM, LotReportFactory.CATCHMENTS_SOURCE, null, ZONED_NOTE + yearNote)
    }

    private fun crimeRate(crime: SuburbCrime?, incidents: Int?): Attribute<CrimeRate> {
        if (crime == null || incidents == null) return Attribute.unknown(CRIME_SOURCE, note = CRIME_NOT_LOADED_NOTE)
        val population = crime.population?.takeIf { it > 0 }
        val rate = CrimeRate(incidents, CRIME_MONTHS, population?.let { incidents * PER_THOUSAND / it }, population)
        val confidence = when {
            population == null || population < CRIME_LOW_BELOW -> Confidence.LOW
            population < CRIME_HIGH_FROM -> Confidence.MEDIUM
            else -> Confidence.HIGH
        }
        val basis = if (population == null) "No population figure for this suburb, so no rate per 1,000 residents." else "Rate per 1,000 of the suburb's $population residents (SEIFA 2021)."
        return Attribute(rate, confidence, CRIME_SOURCE, crime.asOf, "${crime.windowText}. $basis $CRIME_CAVEAT")
    }

    /** One of the other three SEIFA indexes as a decile; unknown when SEIFA is not loaded, or when the ABS gave this suburb no score for the index. */
    private fun seifaDecile(seifa: SuburbSeifa?, decile: Int?, meaning: String): Attribute<Int> {
        if (seifa == null) return Attribute.unknown(SEIFA_SOURCE, note = SEIFA_NOT_LOADED_NOTE)
        if (decile == null) return Attribute.unknown(SEIFA_SOURCE, CENSUS_DATE, SEIFA_NO_SCORE_NOTE)
        return Attribute(
            decile, seifaConfidence(seifa.population), SEIFA_SOURCE, CENSUS_DATE,
            "$meaning Describes the suburb's ${seifa.population} residents as a group and ranks suburbs across Australia. $CENSUS_ATTRIBUTION",
        )
    }

    private fun seifaConfidence(population: Int): Confidence = when {
        population < SEIFA_LOW_BELOW -> Confidence.LOW
        population < SEIFA_HIGH_FROM -> Confidence.MEDIUM
        else -> Confidence.HIGH
    }

    private fun socioEconomic(seifa: SuburbSeifa?): Attribute<SocioEconomic> {
        if (seifa == null) return Attribute.unknown(SEIFA_SOURCE, note = SEIFA_NOT_LOADED_NOTE)
        return Attribute(
            SocioEconomic(seifa.irsadDecile, seifa.irsadScore, seifa.population), seifaConfidence(seifa.population), SEIFA_SOURCE, CENSUS_DATE,
            "Describes the suburb's ${seifa.population} residents as a group, not any one household, and ranks suburbs across Australia " +
                "(decile 1 is the most disadvantaged tenth, 10 the most advantaged). Score ${Math.round(seifa.irsadScore)} on a scale averaging 1,000. $CENSUS_ATTRIBUTION",
        )
    }

    private fun rentedShare(census: SuburbCensus?): Attribute<Double> {
        if (census == null) return Attribute.unknown(CENSUS_SOURCE, note = CENSUS_NOT_LOADED_NOTE)
        val share = census.rentedShare
            ?: return Attribute.unknown(CENSUS_SOURCE, CENSUS_DATE, "No occupied homes with a stated tenure in the 2021 Census")
        return Attribute(
            share, confidenceForSamples(census.tenureStated), CENSUS_SOURCE, CENSUS_DATE,
            "Share of the ${census.tenureStated} occupied private dwellings with a stated tenure that were rented on Census night. " +
                "$SMALL_COUNT_NOTE. $CENSUS_ATTRIBUTION",
        )
    }

    private fun dwellingMix(census: SuburbCensus?): Attribute<DwellingMix> {
        if (census == null) return Attribute.unknown(CENSUS_SOURCE, note = CENSUS_NOT_LOADED_NOTE)
        val base = census.structureStated
        if (base <= 0) return Attribute.unknown(CENSUS_SOURCE, CENSUS_DATE, "No occupied private dwellings with a stated structure in the 2021 Census")
        val mix = DwellingMix(census.houses.toDouble() / base, census.semis.toDouble() / base, census.flats.toDouble() / base)
        return Attribute(
            mix, confidenceForSamples(base), CENSUS_SOURCE, CENSUS_DATE,
            "Of $base occupied private dwellings: houses are separate houses; townhouses include semi-detached, row and terrace houses; units are flats and apartments. " +
                "Other dwellings such as caravans are counted but not shown. $SMALL_COUNT_NOTE. $CENSUS_ATTRIBUTION",
        )
    }

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

    const val LATE_SALES_NOTE: String = "Sales reach the Valuer General weeks after contracts, so the latest weeks are thin"
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
                enrolments = it.enrolments, website = safeWebsite(it.website), levelLabel = it.levelLabel, locality = it.townSuburb,
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

    const val NO_PARK_NOTE: String = "No park of at least 0.5 ha is mapped in the suburb in the OpenStreetMap data loaded, which may be incomplete"

    private fun parks(amenity: SuburbAmenity?): Attribute<Double> {
        if (amenity == null) return osmNotLoaded()
        val share = amenity.parkShare
            ?: return Attribute.unknown(LotReportFactory.OSM_SOURCE, amenity.osmAsOf, "The suburb has no area to measure parks against")
        if (share <= 0.0) {
            // Zero would read as "no park here" when it usually means "none mapped" (58 of 173 pilot suburbs), so it is unknown with that reason.
            return Attribute.unknown(LotReportFactory.OSM_SOURCE, amenity.osmAsOf, NO_PARK_NOTE)
        }
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

    /** The 12 months end on the latest sale in the data (V17), so the date says how recent the figures really are. */
    private fun window(asOf: kotlinx.datetime.LocalDate?): String = "the 12 months to the latest sale on file ($asOf)"

    private fun <T> salesNotLoaded(): Attribute<T> = Attribute.unknown(SALES_SOURCE, note = SALES_NOT_LOADED_NOTE)

    /** True when sales have been loaded; without a latest file date there is nothing to say about prices. */
    private fun SuburbStats?.hasSales(): Boolean = this?.asOf != null

    private fun median(stats: SuburbStats?, kind: String, sales: Int?, median: Long?): Attribute<Long> {
        if (stats == null || !stats.hasSales() || sales == null) return salesNotLoaded()
        return if (median != null) {
            Attribute(median, confidenceForSamples(sales), SALES_SOURCE, stats.asOf, "Based on $sales $kind sales in ${window(stats.asOf)}. $LATE_SALES_NOTE")
        } else {
            val note = if (sales == 0) "No $kind sales in ${window(stats.asOf)}" else "Only $sales $kind sales in ${window(stats.asOf)}, too few for a median"
            Attribute.unknown(SALES_SOURCE, stats.asOf, note)
        }
    }

    private fun priceChange(stats: SuburbStats?): Attribute<Double> {
        if (stats == null || !stats.hasSales()) return salesNotLoaded()
        val change = stats.houseChangePercent
        val basis = "${stats.houseSales} house sales in ${window(stats.asOf)} and ${stats.housePrevSales} in the 12 months before"
        return if (change != null) {
            Attribute(change, confidenceForSamples(minOf(stats.houseSales, stats.housePrevSales)), SALES_SOURCE, stats.asOf, "House median price, from $basis")
        } else {
            Attribute.unknown(SALES_SOURCE, stats.asOf, "Too few house sales to compare two 12-month periods ($basis)")
        }
    }

    private fun salesCount(stats: SuburbStats?): Attribute<Int> {
        if (stats == null || !stats.hasSales()) return salesNotLoaded()
        val note = "${stats.houseSales} house and ${stats.unitSales} unit sales in ${window(stats.asOf)}. $LATE_SALES_NOTE"
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
