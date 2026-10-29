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
data class SuburbSchools(val publicSchoolsInside: List<School>, val catchments: List<School>)

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
)
