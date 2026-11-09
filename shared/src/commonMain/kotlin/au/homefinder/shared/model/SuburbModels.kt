package au.homefinder.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Block size spread across the suburb's lots: median and 10th to 90th percentile, in square metres. */
@Serializable
data class BlockSizeSpread(val medianM2: Double, val p10M2: Double, val p90M2: Double) {
    init {
        require(p10M2 <= medianM2 && medianM2 <= p90M2) { "Block size must satisfy p10 <= median <= p90" }
    }
}

/** Shares are fractions from 0 to 1. [mappedCoverageShare] is how much of the suburb has flood mapping at all. */
@Serializable
data class SuburbFloodShare(val lotsInFloodExtentShare: Double, val mappedCoverageShare: Double) {
    init {
        require(lotsInFloodExtentShare in 0.0..1.0 && mappedCoverageShare in 0.0..1.0) { "Shares must be within 0 to 1" }
    }
}

/** [likelyBrickShare] of lots that are likely brick, and [evidenceShare] of lots with enough evidence to say. */
@Serializable
data class SuburbBrickShare(val likelyBrickShare: Double, val evidenceShare: Double) {
    init {
        require(likelyBrickShare in 0.0..1.0 && evidenceShare in 0.0..1.0) { "Shares must be within 0 to 1" }
    }
}

@Serializable
data class SuburbBeachDistance(val fromCentreM: Int, val fromNearestEdgeM: Int)

@Serializable
data class SuburbSchools(
    val publicSchoolsInside: List<School>,
    val catchments: List<School>,
    /** The mean ICSEA of the schools that have one (rounded), or null when none has. */
    val averageIcsea: Int? = null,
)

/** Shopping centres and supermarkets inside the suburb or within 2 km. */
@Serializable
data class SuburbShopping(val shoppingCentres: Int, val supermarkets: Int)

@Serializable
data class SuburbTransport(
    val trainStations: Int,
    val lightRailStations: Int,
    val ferryWharves: Int,
    val busStops: Int,
    val bestStopPeakDeparturesPerHour: Double? = null,
)

/**
 * A Homely star score for a suburb, typed in by you (ADR 0028): [stars] from 0 to 5 to one decimal place and, if you noted
 * it, how many [reviewCount] reviews it rests on. It is an opinion of Homely's users, never fetched from Homely.
 */
@Serializable
data class HomelyRating(val stars: Double, val reviewCount: Int? = null) {
    init {
        require(stars.isFinite() && stars >= 0.0 && stars <= MAX_STARS) { "Stars must be from 0 to $MAX_STARS" }
        require(reviewCount == null || reviewCount in 0..MAX_REVIEWS) { "Review count must be from 0 to $MAX_REVIEWS" }
    }

    companion object {
        const val MAX_STARS: Double = 5.0
        const val MAX_REVIEWS: Int = 1_000_000
        const val SOURCE: String = "Homely (entered by you)"
    }
}

/** A rated suburb with the point its map label sits at (the same point as its name). */
@Serializable
data class SuburbRatingPoint(val suburbId: String, val name: String, val rating: HomelyRating, val latitude: Double, val longitude: Double)

@Serializable
data class SuburbRatingsResponse(val ratings: List<SuburbRatingPoint>)

/**
 * What the filter panel needs from one suburb (ADR 0035, 0046). A value is null when it is unknown for the suburb: no school with an ICSEA,
 * no beach found, no park mapped, no Census figure, or that data has not been loaded at all.
 */
@Serializable
data class SuburbFilterValue(
    val suburbId: String,
    val averageIcsea: Int? = null,
    /** Straight-line distance in kilometres from the suburb's centre to the nearest ocean beach. */
    val beachKm: Double? = null,
    /** The share of the suburb that is park (parks of at least 0.5 ha), from 0 to 1. */
    val parkShare: Double? = null,
    /** The share of occupied private dwellings that are rented (2021 Census), from 0 to 1. */
    val rentedShare: Double? = null,
    /** The SEIFA decile of socio-economic advantage (1 most disadvantaged, 10 most advantaged; ADR 0049). */
    val seifaDecile: Int? = null,
    /** Recorded violent incidents per 1,000 residents over 24 months (BOCSAR); null for a suburb under 500 residents or with no population (ADR 0051). */
    val violentPer1000: Double? = null,
    /** Recorded break-in, vehicle theft, theft from cars and homes and malicious damage incidents per 1,000 residents over 24 months (ADR 0051). */
    val propertyPer1000: Double? = null,
    /** The suburb's name, for the score ranking (ADR 0047). */
    val name: String = "",
    /** The average ICSEA of the schools whose catchments cover the suburb, weighted by how much of it each covers (ADR 0062); null when none has one. */
    val zonedIcsea: Int? = null,
    /**
     * The average of the NAPLAN figures you typed (ADR 0068, 0069) for the zoned schools the Suburb Card lists, weighted by how much of the suburb each
     * zone covers, in points from the national average (ADR 0074); null when none of those schools has a figure.
     */
    val zonedNaplan: Int? = null,
    /** Where the suburb's name is drawn on the map (GDA2020 degrees), the point the ratings list uses too; the map puts the NAPLAN label there (ADR 0076). */
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** Where one criterion's figures come from, as on the card. */
@Serializable
data class FilterProvenance(val source: String, val asOf: LocalDate? = null)

/**
 * Every suburb's filter values, with where each criterion's figures come from (provenance, as on the card). [icseaAsOf] is null and
 * every [SuburbFilterValue.averageIcsea] is null when the schools data has not been loaded; [beach], [parks] and [rented] are null when
 * their data has not been loaded (the OpenStreetMap roll-up for the first two, the Census for the third).
 */
@Serializable
data class SuburbFiltersResponse(
    val suburbs: List<SuburbFilterValue>,
    val icseaSource: String,
    val icseaAsOf: LocalDate? = null,
    val beach: FilterProvenance? = null,
    val parks: FilterProvenance? = null,
    val rented: FilterProvenance? = null,
    val seifa: FilterProvenance? = null,
    val crime: FilterProvenance? = null,
    val zoned: FilterProvenance? = null,
    /** Present once the catchments are loaded and figures can be typed in, even before any is (ADR 0074); its date is the last figure saved. */
    val naplan: FilterProvenance? = null,
)

/** The same factors as a property, rolled up to suburb level. Every statistic carries its own provenance. */
@Serializable
data class SuburbReport(
    val suburbId: String,
    val name: String,
    val postcode: String?,
    val areaKm2: Attribute<Double>,
    val medianHousePriceAud: Attribute<Long>,
    val medianUnitPriceAud: Attribute<Long>,
    val priceChange12MonthsPercent: Attribute<Double>,
    val salesCount: Attribute<Int>,
    val blockSize: Attribute<BlockSizeSpread>,
    val floodShare: Attribute<SuburbFloodShare>,
    val brickShare: Attribute<SuburbBrickShare>,
    val beach: Attribute<SuburbBeachDistance>,
    val schools: Attribute<SuburbSchools>,
    val parkAreaShare: Attribute<Double>,
    val shopping: Attribute<SuburbShopping>,
    val transport: Attribute<SuburbTransport>,
    /** Present only when you have entered one; unknown ("Not entered") otherwise. Last, with a default, so older reports still decode. */
    val homelyRating: Attribute<HomelyRating> = Attribute.unknown(HomelyRating.SOURCE, note = "Not entered"),
    /** The share of occupied private dwellings that are rented, from 0 to 1 (2021 Census, ADR 0044). */
    val rentedShare: Attribute<Double> = Attribute.unknown("ABS 2021 Census", note = "Not computed yet"),
    /** What the occupied private dwellings are: separate houses, townhouses and semis, or flats and units (2021 Census, ADR 0044). */
    val dwellingMix: Attribute<DwellingMix> = Attribute.unknown("ABS 2021 Census", note = "Not computed yet"),
    /** How advantaged the suburb's residents are as a group, from the ABS SEIFA 2021 (ADR 0049). */
    val socioEconomic: Attribute<SocioEconomic> = Attribute.unknown("ABS SEIFA 2021", note = "Not computed yet"),
    /** The SEIFA decile of disadvantage alone (IRSD): 1 the most disadvantaged tenth of areas, 10 the least (ADR 0050). */
    val disadvantage: Attribute<Int> = Attribute.unknown("ABS SEIFA 2021", note = "Not computed yet"),
    /** The SEIFA decile of economic resources (IER): 1 the fewest income, housing and asset resources, 10 the most (ADR 0050). */
    val economicResources: Attribute<Int> = Attribute.unknown("ABS SEIFA 2021", note = "Not computed yet"),
    /** The SEIFA decile of education and occupation (IEO): 1 the lowest qualifications and skills, 10 the highest (ADR 0050). */
    val educationOccupation: Attribute<Int> = Attribute.unknown("ABS SEIFA 2021", note = "Not computed yet"),
    /** Recorded violent crime (assaults, robberies, sexual offences) over 24 months, from BOCSAR (ADR 0051). */
    val violentCrime: Attribute<CrimeRate> = Attribute.unknown("NSW BOCSAR", note = "Not computed yet"),
    /** Recorded break-ins, vehicle theft, theft from cars and homes and malicious damage over 24 months, from BOCSAR (ADR 0051). */
    val propertyCrime: Attribute<CrimeRate> = Attribute.unknown("NSW BOCSAR", note = "Not computed yet"),
    /** The public school catchments that cover most of the suburb's lots (ADR 0062). */
    val zonedSchools: Attribute<SuburbZonedSchools> = Attribute.unknown("NSW Department of Education", note = "Not computed yet"),
)

/**
 * The school catchments (intake zones) that cover the suburb's lots: [zones] primary first, then high, each by how much of the suburb it covers, largest first.
 * A level with no zone covering a quarter of the suburb still lists its biggest one. [enrolmentYear] is the year the zones apply to.
 */
@Serializable
data class SuburbZonedSchools(val zones: List<SuburbZone>, val enrolmentYear: Int? = null)

/**
 * One zone: the school (its [School.levelLabel] says the kind of zone and the years it serves, for example "primary, K-6") and [lotShare], from 0 to 1, the
 * share of the suburb's lots whose representative point lies inside the zone. A suburb split between zones has several, each below 1.
 */
@Serializable
data class SuburbZone(val school: School, val lotShare: Double) {
    init {
        require(lotShare in 0.0..1.0) { "A share must be from 0 to 1" }
    }
}

/**
 * Recorded incidents of one kind of crime in a suburb over [months] months. [perThousand] is the rate per 1,000 residents, null when the suburb has no
 * population figure to divide by; [population] is the figure used.
 */
@Serializable
data class CrimeRate(val incidents: Int, val months: Int, val perThousand: Double? = null, val population: Int? = null) {
    init {
        require(incidents >= 0 && months > 0) { "Incidents cannot be negative and the window must be at least a month" }
    }
}

/**
 * The Index of Relative Socio-economic Advantage and Disadvantage (IRSAD) for a suburb: [decile] from 1 (most disadvantaged tenth of areas
 * in Australia) to 10 (most advantaged), the [score] on the ABS's arbitrary scale (average 1,000) and the [population] it rests on.
 */
@Serializable
data class SocioEconomic(val decile: Int, val score: Double, val population: Int) {
    init {
        require(decile in 1..10) { "Decile must be from 1 to 10" }
    }
}

/**
 * Shares of the suburb's occupied private dwellings by structure, each from 0 to 1. Houses are separate houses; [townhouseShare] covers
 * semi-detached, row and terrace houses and townhouses; [unitShare] covers flats, units and apartments. Other dwellings (caravans, cabins,
 * flats attached to shops) are in the base but not shown, so the three need not add to 1.
 */
@Serializable
data class DwellingMix(val houseShare: Double, val townhouseShare: Double, val unitShare: Double) {
    init {
        require(houseShare in 0.0..1.0 && townhouseShare in 0.0..1.0 && unitShare in 0.0..1.0) { "Shares must be from 0 to 1" }
        require(houseShare + townhouseShare + unitShare <= 1.0 + ROUNDING) { "The shares cannot add to more than a whole" }
    }

    private companion object {
        const val ROUNDING = 1e-9
    }
}
