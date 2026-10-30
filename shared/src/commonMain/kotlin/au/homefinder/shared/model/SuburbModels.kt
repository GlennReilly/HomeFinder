package au.homefinder.shared.model

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
)
