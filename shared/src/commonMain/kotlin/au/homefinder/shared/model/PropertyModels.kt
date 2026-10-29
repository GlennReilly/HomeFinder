package au.homefinder.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Aspect of the backyard. [bearingDegrees] runs from the dwelling centroid to the rear boundary midpoint, clockwise
 * from true north (0 up to but excluding 360). North-facing means the garden lies within 45 degrees of north.
 */
@Serializable
data class AspectResult(
    val bearingDegrees: Double,
    val isNorthFacing: Boolean,
    val lotShape: LotShape,
) {
    init {
        require(bearingDegrees >= 0.0 && bearingDegrees < FULL_TURN_DEGREES) { "Bearing must be in [0, 360)" }
    }

    val isAmbiguous: Boolean get() = lotShape != LotShape.REGULAR

    companion object {
        const val NORTH_FACING_TOLERANCE_DEGREES: Double = 45.0
        private const val FULL_TURN_DEGREES = 360.0

        fun isNorthFacing(bearingDegrees: Double): Boolean {
            val fromNorth = minOf(bearingDegrees, FULL_TURN_DEGREES - bearingDegrees)
            return fromNorth <= NORTH_FACING_TOLERANCE_DEGREES
        }

        fun of(bearingDegrees: Double, lotShape: LotShape): AspectResult =
            AspectResult(bearingDegrees, isNorthFacing(bearingDegrees), lotShape)
    }
}

/** A flood result: the class and, where a study gives it, the annual exceedance probability of the event in percent. */
@Serializable
data class FloodRisk(val floodClass: FloodClass, val aepPercent: Double? = null)

@Serializable
data class SlopeSummary(
    val meanPercent: Double,
    val maxPercent: Double,
    val slopeClass: SlopeClass,
    val fall: FallDirection,
) {
    init {
        require(meanPercent >= 0.0 && maxPercent >= meanPercent) { "Slope must satisfy 0 <= mean <= max" }
    }
}

@Serializable
data class ComparableSale(
    val address: String,
    val soldOn: LocalDate,
    val priceAud: Long,
    val landAreaM2: Double? = null,
    val distanceM: Int? = null,
)

/** An estimated price range in whole AUD dollars with the comparable sales it was built from. */
@Serializable
data class PriceRange(
    val lowAud: Long,
    val midAud: Long,
    val highAud: Long,
    val comparables: List<ComparableSale> = emptyList(),
) {
    init {
        require(lowAud in 0..midAud && midAud <= highAud) { "A price range must satisfy 0 <= low <= mid <= high" }
    }

    val sampleSize: Int get() = comparables.size
}

/** Distance to something, straight line and (where a route exists) on foot. */
@Serializable
data class Proximity(
    val name: String?,
    val category: String? = null,
    val straightLineM: Int,
    val walkingM: Int? = null,
    val walkingMinutes: Int? = null,
    val drivingMinutes: Int? = null,
)

@Serializable
data class ProximityList(val items: List<Proximity>)

@Serializable
data class ParkAccess(val nearest: List<Proximity>, val areaWithin800mM2: Double? = null)

@Serializable
data class School(val id: String, val name: String, val sector: SchoolSector, val level: SchoolLevel)

@Serializable
data class NearbySchool(
    val school: School,
    val distanceM: Int,
    val walkingMinutes: Int? = null,
    val drivingMinutes: Int? = null,
)

@Serializable
data class SchoolInfo(
    val primaryCatchment: School? = null,
    val highCatchment: School? = null,
    val nearest: List<NearbySchool> = emptyList(),
)

@Serializable
data class TransportStop(
    val mode: TransportMode,
    val name: String,
    val distanceM: Int,
    val walkingMinutes: Int? = null,
    val peakDeparturesPerHour: Double? = null,
)

@Serializable
data class TransportInfo(val nearest: List<TransportStop>, val travelToWorkMinutes: Int? = null)

@Serializable
data class PropertyReport(
    val addressId: String,          // G-NAF PID
    val lotId: String,
    val suburbId: String,
    val wallMaterial: Attribute<WallMaterial>,
    val backyardAspect: Attribute<AspectResult>,
    val flood: Attribute<FloodRisk>,
    val blockAreaM2: Attribute<Double>,
    val slope: Attribute<SlopeSummary>,
    val estimatedPriceAud: Attribute<PriceRange>,
    val beach: Attribute<Proximity>,
    val schools: Attribute<SchoolInfo>,
    val parks: Attribute<ParkAccess>,
    val shopping: Attribute<ProximityList>,
    val transport: Attribute<TransportInfo>,
)
