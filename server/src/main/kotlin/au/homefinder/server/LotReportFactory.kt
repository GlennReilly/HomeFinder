package au.homefinder.server

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.ParkAccess
import au.homefinder.shared.model.Proximity
import au.homefinder.shared.model.ProximityList
import au.homefinder.shared.model.SlopeClass
import au.homefinder.shared.model.SlopeSummary
import kotlin.math.roundToInt

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
    const val OSM_SOURCE: String = "OpenStreetMap contributors (ODbL)"
    const val OSM_NOT_LOADED_NOTE: String = "OpenStreetMap data not loaded yet"

    /** A walking time is estimated from the straight line, not routed (ADR 0023): a detour factor at a walking pace. */
    const val WALKING_DETOUR_FACTOR: Double = 1.3
    const val WALKING_METRES_PER_MINUTE: Double = 80.0
    const val WALKING_ESTIMATE_NOTE: String =
        "straight-line distance; walking times are estimates (straight line x 1.3 at 80 m a minute), not routes"

    /** The pipeline's park radius (config/osm.env PARK_RADIUS_M); the model's field is named for it. */
    private const val PARK_RADIUS_NOTE = "within 800 m"
    private const val FLOOD_SOURCE = "NSW Flood Data Portal and council studies"
    private const val ASPECT_SOURCE = "NSW cadastre lot, building footprint and road centrelines"
    const val DEM_SOURCE: String = "Geoscience Australia 5 m LiDAR DEM (CC BY 4.0)"
    const val DEM_NOT_LOADED_NOTE: String = "Elevation data not loaded yet"
    const val DEM_NO_COVERAGE_NOTE: String = "No elevation data covers this lot (the LiDAR surveys behind the model do not reach everywhere)"
    const val ELEVATION_NOTE: String =
        "Mean height above sea level of the lot's 5 m pixels. Bare ground from LiDAR surveys between 2001 and 2015, so buildings and later earthworks are not shown"
    const val SLOPE_NOTE: String =
        "Rise over run between neighbouring 5 m pixels, averaged over the lot; the steepest step is the maximum. Which way the lot falls is not worked out yet"
    private const val PRICE_SOURCE = "NSW Valuer General"
    private const val LISTING_SOURCE = "Licensed listing text"
    private const val EDUCATION_SOURCE = "NSW Department of Education"
    private const val TRANSPORT_SOURCE = "Transport for NSW"

    /** Strata and community plans (SP and CP) share one parcel across many titles. */
    private val STRATA_PLAN = Regex("(^|/)(SP|CP)")

    fun isStrata(parcelKey: String?): Boolean = parcelKey != null && STRATA_PLAN.containsMatchIn(parcelKey)

    private fun <T> unknown(source: String): Attribute<T> = Attribute.unknown(source, note = NOT_COMPUTED_NOTE)

    fun build(record: LotRecord, proximity: LotProximity? = null, terrain: LotTerrainData? = null): LotReport {
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
            slope = slope(terrain),
            estimatedPriceAud = unknown(PRICE_SOURCE),
            beach = beach(proximity),
            schools = unknown(EDUCATION_SOURCE),
            parks = parks(proximity),
            shopping = shopping(proximity),
            transport = unknown(TRANSPORT_SOURCE),
            elevationM = elevation(terrain),
        )
    }

    private fun <T> demUnknown(terrain: LotTerrainData?): Attribute<T> = when {
        terrain == null -> Attribute.unknown(DEM_SOURCE, note = DEM_NOT_LOADED_NOTE)
        else -> Attribute.unknown(DEM_SOURCE, terrain.loadedOn, DEM_NO_COVERAGE_NOTE)
    }

    private fun elevation(data: LotTerrainData?): Attribute<Double> {
        val terrain = data?.terrain ?: return demUnknown(data)
        return Attribute(terrain.elevationM, Confidence.MEDIUM, DEM_SOURCE, data.loadedOn, ELEVATION_NOTE)
    }

    private fun slope(data: LotTerrainData?): Attribute<SlopeSummary> {
        val terrain = data?.terrain ?: return demUnknown(data)
        val mean = terrain.meanSlopePercent ?: return demUnknown(data)
        val max = maxOf(terrain.maxSlopePercent ?: mean, mean)
        return Attribute(SlopeSummary(mean, max, SlopeClass.fromMeanPercent(mean)), Confidence.MEDIUM, DEM_SOURCE, data.loadedOn, SLOPE_NOTE)
    }

    private fun <T> osmNotLoaded(): Attribute<T> = Attribute.unknown(OSM_SOURCE, note = OSM_NOT_LOADED_NOTE)

    private fun <T> osmNone(proximity: LotProximity, what: String): Attribute<T> =
        Attribute.unknown(OSM_SOURCE, proximity.osmAsOf, "No $what found in the OpenStreetMap data loaded")

    fun walkingMinutes(straightLineM: Int): Int =
        (straightLineM * WALKING_DETOUR_FACTOR / WALKING_METRES_PER_MINUTE).roundToInt()

    private fun proximityOf(feature: NearFeature, category: String, fallbackName: String?) = Proximity(
        name = feature.name ?: fallbackName,
        category = category,
        straightLineM = feature.distanceM.roundToInt(),
        walkingMinutes = walkingMinutes(feature.distanceM.roundToInt()),
    )

    private fun beach(proximity: LotProximity?): Attribute<Proximity> {
        if (proximity == null) return osmNotLoaded()
        val nearest = proximity.of(LotProximity.BEACH).firstOrNull() ?: return osmNone(proximity, "ocean beach")
        return Attribute(
            proximityOf(nearest, "ocean beach", null), Confidence.MEDIUM, OSM_SOURCE, proximity.osmAsOf,
            "Nearest beach mapped on the coastline; $WALKING_ESTIMATE_NOTE",
        )
    }

    private fun parks(proximity: LotProximity?): Attribute<ParkAccess> {
        if (proximity == null) return osmNotLoaded()
        val nearest = proximity.of(LotProximity.PARK).map { proximityOf(it, "park", null) }
        if (nearest.isEmpty()) return osmNone(proximity, "park of at least 0.5 ha")
        return Attribute(
            ParkAccess(nearest, proximity.parkAreaM2), Confidence.MEDIUM, OSM_SOURCE, proximity.osmAsOf,
            "Parks of at least 0.5 ha, nearest first; park area $PARK_RADIUS_NOTE; $WALKING_ESTIMATE_NOTE",
        )
    }

    private fun shopping(proximity: LotProximity?): Attribute<ProximityList> {
        if (proximity == null) return osmNotLoaded()
        val items = listOfNotNull(
            proximity.of(LotProximity.SUPERMARKET).firstOrNull()?.let { proximityOf(it, "supermarket", null) },
            proximity.of(LotProximity.MALL).firstOrNull()?.let { proximityOf(it, "shopping centre", null) },
        )
        if (items.isEmpty()) return osmNone(proximity, "supermarket or shopping centre")
        return Attribute(
            ProximityList(items), Confidence.MEDIUM, OSM_SOURCE, proximity.osmAsOf,
            "Nearest supermarket and shopping centre mapped in OpenStreetMap, which may be incomplete; $WALKING_ESTIMATE_NOTE",
        )
    }
}
