package au.homefinder.server

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.DrainageHint
import au.homefinder.shared.model.DrainageProximity
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.NearbySchool
import au.homefinder.shared.model.School
import au.homefinder.shared.model.SchoolInfo
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolSector
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
    const val DRAINAGE_NOT_LOADED_NOTE: String = "Height above drainage not computed yet"
    const val DRAINAGE_NO_RESULT_NOTE: String =
        "No result for this lot: no elevation data covers it, or its water leaves the mapped area before meeting a stream"
    const val DRAINAGE_NOTE: String =
        "How far the lot's ground stands above the first stream its water reaches, from the 5 m LiDAR DEM; a stream is where at least 5 ha drain through one pixel. " +
            "An indication of where water runs, not a flood result: it ignores drains, culverts and levees, and the LiDAR surveys end in 2015. " +
            "Checked against the council's 10% annual-chance flood corridor in ELERMORE VALE only (ADR 0040): under 1 m touched it for 43% of lots, over 5 m for under 1%"
    /** More than this share of a suburb's lots under 1 m above drainage means the suburb is too flat for the hint (ADR 0054). */
    const val FLAT_SUBURB_SHARE_ABOVE: Double = 0.5

    /** Under this many lots with a result a suburb's share is not trusted, and the hint is shown as usual. */
    const val FLAT_SUBURB_MIN_LOTS: Int = 20
    private const val PERCENT = 100.0
    const val FLAT_AREA_NOTE: String =
        "Not offered here: most lots in this suburb read under 1 m above drainage, so the hint cannot distinguish between them, and the council's flood maps " +
            "for flat land show overland flow, ponding and tidal water, which it does not measure (ADR 0054). Not a flood result"
    private const val PRICE_SOURCE = "NSW Valuer General"
    private const val LISTING_SOURCE = "Licensed listing text"
    private const val TRANSPORT_SOURCE = "Transport for NSW"

    /** Strata and community plans (SP and CP) share one parcel across many titles. */
    private val STRATA_PLAN = Regex("(^|/)(SP|CP)")

    fun isStrata(parcelKey: String?): Boolean = parcelKey != null && STRATA_PLAN.containsMatchIn(parcelKey)

    private fun <T> unknown(source: String): Attribute<T> = Attribute.unknown(source, note = NOT_COMPUTED_NOTE)

    fun build(
        record: LotRecord,
        proximity: LotProximity? = null,
        terrain: LotTerrainData? = null,
        drainage: LotDrainageData? = null,
        schools: LotSchoolData? = null,
        catchments: LotCatchmentData? = null,
    ): LotReport {
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
            schools = schools(schools, catchments),
            parks = parks(proximity),
            shopping = shopping(proximity),
            transport = unknown(TRANSPORT_SOURCE),
            elevationM = elevation(terrain),
            heightAboveDrainage = heightAboveDrainage(drainage),
        )
    }

    /** Low confidence on purpose: it is a proxy checked in one suburb (ADR 0040), and never a flood result. */
    private fun heightAboveDrainage(data: LotDrainageData?): Attribute<DrainageHint> {
        if (data == null) return Attribute.unknown(DEM_SOURCE, note = DRAINAGE_NOT_LOADED_NOTE)
        val drainage = data.drainage ?: return Attribute.unknown(DEM_SOURCE, data.computedOn, DRAINAGE_NO_RESULT_NOTE)
        val flat = data.suburbLotsWithResult >= FLAT_SUBURB_MIN_LOTS && (data.suburbUnderOneMetreShare ?: 0.0) > FLAT_SUBURB_SHARE_ABOVE
        if (flat) {
            val hint = DrainageHint(drainage.heightAboveDrainageM, drainage.streamCatchmentHa, DrainageProximity.FLAT_AREA)
            val percent = Math.round((data.suburbUnderOneMetreShare ?: 0.0) * PERCENT)
            return Attribute(hint, Confidence.NONE, DEM_SOURCE, data.computedOn, "$percent% of this suburb's lots read under 1 m. $FLAT_AREA_NOTE")
        }
        val hint = DrainageHint(
            drainage.heightAboveDrainageM, drainage.streamCatchmentHa, DrainageProximity.fromHeightM(drainage.heightAboveDrainageM),
        )
        return Attribute(hint, Confidence.LOW, DEM_SOURCE, data.computedOn, DRAINAGE_NOTE)
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

    const val LOT_SCHOOLS_NOTE: String =
        "The nearest NSW public primary school and the nearest public high school, by straight line to the lot, not catchments or routes. " +
            "$WALKING_ESTIMATE_NOTE. Private and Catholic schools are not in this data. ICSEA measures how socio-educationally advantaged a school's students are (national average 1000), not how good the school is"
    const val LOT_SCHOOLS_NONE_NOTE: String = "No public school found near this lot in the data loaded"

    /** The nearest primary and the nearest high school, nearest first; where the data has neither kind, the nearest two of any kind. */
    fun chooseSchools(candidates: List<NearSchool>): List<NearSchool> {
        val sorted = candidates.sortedBy { it.distanceM }
        val primary = sorted.firstOrNull { SuburbReportFactory.schoolLevel(it.school.levelLabel) == SchoolLevel.PRIMARY }
        val high = sorted.firstOrNull { SuburbReportFactory.schoolLevel(it.school.levelLabel) == SchoolLevel.HIGH }
        return listOfNotNull(primary, high).sortedBy { it.distanceM }.ifEmpty { sorted.take(FALLBACK_SCHOOLS) }
    }

    private const val FALLBACK_SCHOOLS = 2

    const val CATCHMENTS_SOURCE: String = "NSW Department of Education school intake zones (CC BY 3.0 Australia) and public schools master dataset; ICSEA from ACARA"
    const val LOT_ZONED_NOTE: String =
        "Zoned schools are the NSW public school catchments (intake zones) this lot lies in. Catchments change, being in a zone does not guarantee a place, " +
            "and out-of-area enrolment is common: check with the school. Where a level shows no zone, the nearest public school of that level is shown instead, " +
            "by straight line, not a catchment. Private and Catholic schools are not in this data. ICSEA measures how socio-educationally advantaged a school's students are " +
            "(national average 1000), not how good the school is. Based on NSW Department of Education data"
    const val LOT_ZONED_NONE_NOTE: String = "No catchment covers this lot and no public school was found nearby in the data loaded"

    /** The Department's zone type in plain words: "primary", "infants", "high, girls", "central school, primary years". */
    fun zoneKind(catchType: String): String = when (catchType) {
        "PRIMARY" -> "primary"
        "INFANTS" -> "infants"
        "CENTRAL_PRIMARY" -> "central school, primary years"
        "HIGH_COED" -> "high"
        "HIGH_BOYS" -> "high, boys"
        "HIGH_GIRLS" -> "high, girls"
        "CENTRAL_HIGH" -> "central school, high years"
        else -> catchType.lowercase().replace('_', ' ')
    }

    internal fun zonedSchool(zone: CatchmentZone): School {
        val record = zone.school
        val label = listOfNotNull(zoneKind(zone.catchType), zone.years?.takeIf { it.isNotBlank() }).joinToString(", ")
        return School(
            id = zone.schoolCode.toString(), name = record?.name ?: zone.name, sector = SchoolSector.PUBLIC,
            level = if (zone.level == "HIGH") SchoolLevel.HIGH else SchoolLevel.PRIMARY,
            icsea = record?.icsea, enrolments = record?.enrolments, website = SuburbReportFactory.safeWebsite(record?.website), levelLabel = label,
            locality = record?.townSuburb,
        )
    }

    private fun nearestSchools(data: LotSchoolData?): List<NearbySchool> = chooseSchools(data?.candidates.orEmpty()).map { near ->
        val record = near.school
        val metres = near.distanceM.roundToInt()
        NearbySchool(
            School(
                id = record.code.toString(), name = record.name, sector = SchoolSector.PUBLIC, level = SuburbReportFactory.schoolLevel(record.levelLabel),
                icsea = record.icsea, enrolments = record.enrolments, website = SuburbReportFactory.safeWebsite(record.website), levelLabel = record.levelLabel,
                locality = record.townSuburb,
            ),
            distanceM = metres, walkingMinutes = walkingMinutes(metres),
        )
    }

    /**
     * The Schools row. With no catchments loaded it is the nearest primary and high school (ADR 0045). With them (ADR 0056) it lists the zones the lot
     * lies in, and keeps a nearest school only for a level (primary or high) the lot has no zone for, labelled as not zoned.
     */
    private fun schools(data: LotSchoolData?, catchments: LotCatchmentData?): Attribute<SchoolInfo> {
        if (catchments == null) {
            if (data == null) return Attribute.unknown(SuburbReportFactory.SCHOOLS_SOURCE, note = SuburbReportFactory.SCHOOLS_NOT_LOADED_NOTE)
            val nearest = nearestSchools(data)
            if (nearest.isEmpty()) return Attribute.unknown(SuburbReportFactory.SCHOOLS_SOURCE, data.extractedOn, LOT_SCHOOLS_NONE_NOTE)
            return Attribute(SchoolInfo(nearest = nearest), Confidence.MEDIUM, SuburbReportFactory.SCHOOLS_SOURCE, data.extractedOn, LOT_SCHOOLS_NOTE)
        }
        val zoned = catchments.zones.sortedWith(compareBy({ if (it.level == "PRIMARY") 0 else 1 }, { it.schoolCode })).map(::zonedSchool)
        val coveredLevels = zoned.map { it.level }.toSet()
        val nearest = nearestSchools(data).filter { it.school.level !in coveredLevels }
        if (zoned.isEmpty() && nearest.isEmpty()) return Attribute.unknown(CATCHMENTS_SOURCE, data?.extractedOn, LOT_ZONED_NONE_NOTE)
        val year = catchments.zones.firstNotNullOfOrNull { it.enrolmentYear }?.let { ". The zones are for $it enrolments" }.orEmpty()
        return Attribute(
            SchoolInfo(nearest = nearest, zoned = zoned, zonesChecked = true), Confidence.MEDIUM, CATCHMENTS_SOURCE, data?.extractedOn, LOT_ZONED_NOTE + year,
        )
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
