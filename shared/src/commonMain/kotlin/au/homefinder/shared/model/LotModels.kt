package au.homefinder.shared.model

import kotlinx.serialization.Serializable

/** A lot as the map knows it: enough to select it before its report is loaded. */
@Serializable
data class LotRef(val lotId: String, val suburbId: String)

/**
 * What the app knows about one cadastral lot. The lot, its suburb, the addresses on it and its measured area are known;
 * every other factor is an explicit unknown until its pipeline lands, as for the Suburb Card. [addresses] are formatted
 * G-NAF addresses (a lot can carry several, for example the units of a strata plan).
 */
@Serializable
data class LotReport(
    val lotId: String,
    /** The parcel label as the cadastre writes it, for example `1//DP100`; null when the cadastre has none. */
    val parcelLabel: String?,
    val suburbId: String,
    val suburbName: String,
    val postcode: String?,
    val addresses: List<String>,
    val blockAreaM2: Attribute<Double>,
    val wallMaterial: Attribute<WallMaterial>,
    val backyardAspect: Attribute<AspectResult>,
    val flood: Attribute<FloodRisk>,
    val slope: Attribute<SlopeSummary>,
    val estimatedPriceAud: Attribute<PriceRange>,
    val beach: Attribute<Proximity>,
    val schools: Attribute<SchoolInfo>,
    val parks: Attribute<ParkAccess>,
    val shopping: Attribute<ProximityList>,
    val transport: Attribute<TransportInfo>,
    /** Mean height above sea level in metres (ADR 0036). */
    val elevationM: Attribute<Double> = Attribute.unknown("Geoscience Australia 5 m LiDAR DEM", note = "Not computed yet"),
)
