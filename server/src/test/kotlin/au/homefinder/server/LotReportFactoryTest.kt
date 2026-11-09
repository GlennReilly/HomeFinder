package au.homefinder.server

import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolSector
import au.homefinder.shared.model.DrainageProximity
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.SlopeClass
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LotReportFactoryTest {
    private val lot = LotRecord(
        "1001", "1//DP100", "1/100", "9001", "HAMILTON", "2303", 561.4, listOf("12 MAIN STREET, HAMILTON NSW 2303"),
    )

    @Test
    fun `the lot, its suburb, addresses and measured area are known`() {
        val report = LotReportFactory.build(lot)
        assertEquals("1001", report.lotId)
        assertEquals("1//DP100", report.parcelLabel)
        assertEquals("HAMILTON", report.suburbName)
        assertEquals("2303", report.postcode)
        assertEquals(listOf("12 MAIN STREET, HAMILTON NSW 2303"), report.addresses)
        assertEquals(561.4, report.blockAreaM2.value)
        assertEquals(Confidence.HIGH, report.blockAreaM2.confidence)
        assertEquals(LotReportFactory.AREA_SOURCE, report.blockAreaM2.source)
        assertFalse("strata" in report.blockAreaM2.note.orEmpty())
    }

    @Test
    fun `a strata lot's area is flagged as the whole parcel's, with lower confidence`() {
        listOf("1/SP12345", "CP999", "5/100/SP1").forEach { key ->
            val area = LotReportFactory.build(lot.copy(parcelKey = key)).blockAreaM2
            assertEquals(Confidence.MEDIUM, area.confidence, key)
            assertTrue(LotReportFactory.STRATA_NOTE in area.note.orEmpty(), key)
        }
        assertFalse(LotReportFactory.isStrata("1/DP100"))
        assertFalse(LotReportFactory.isStrata(null))
    }

    @Test
    fun `without proximity data the OSM factors say the data is not loaded and the rest are not computed`() {
        val report = LotReportFactory.build(lot)
        listOf(report.beach, report.parks, report.shopping).forEach {
            assertTrue(it.isUnknown)
            assertEquals(LotReportFactory.OSM_NOT_LOADED_NOTE, it.note)
            assertEquals(LotReportFactory.OSM_SOURCE, it.source)
        }
        assertTrue(report.schools.isUnknown)
        assertEquals(SuburbReportFactory.SCHOOLS_NOT_LOADED_NOTE, report.schools.note)
        val others = listOf(report.wallMaterial, report.backyardAspect, report.flood, report.estimatedPriceAud, report.transport)
        assertEquals(5, others.size)
        others.forEach {
            assertTrue(it.isUnknown)
            assertEquals(Confidence.NONE, it.confidence)
            assertEquals(LotReportFactory.NOT_COMPUTED_NOTE, it.note)
            assertTrue(it.source.isNotBlank())
        }
    }

    private val day = LocalDate(2026, 10, 29)
    private val nearby = LotProximity(
        listOf(
            NearFeature("beach", 1, "Ocean Beach", 1794.4),
            NearFeature("park", 2, null, 374.0), NearFeature("park", 1, "Big Park", 187.2), NearFeature("park", 3, "Oval", 935.0),
            NearFeature("supermarket", 1, "Near Supermarket", 1112.0), NearFeature("mall", 1, "Hamilton Mall", 561.0),
        ),
        parkAreaM2 = 50_170.0, osmAsOf = day,
    )

    @Test
    fun `nearby features become attributes with distance, estimated walking time, source, date and a note`() {
        val report = LotReportFactory.build(lot, nearby)
        val beach = assertNotNull(report.beach.value)
        assertEquals("Ocean Beach", beach.name)
        assertEquals(1794, beach.straightLineM)
        assertEquals(29, beach.walkingMinutes) // 1794 m x 1.3 / 80 m a minute
        assertEquals(null, beach.walkingM)
        assertEquals(Confidence.MEDIUM, report.beach.confidence)
        assertEquals(day, report.beach.asOf)
        assertTrue("estimates" in report.beach.note.orEmpty() && "not routes" in report.beach.note.orEmpty())

        val parks = assertNotNull(report.parks.value)
        assertEquals(listOf("Big Park", null, "Oval"), parks.nearest.map { it.name }, "ranked nearest first, an unnamed park keeps a null name")
        assertEquals(listOf(187, 374, 935), parks.nearest.map { it.straightLineM })
        assertEquals(50_170.0, parks.areaWithin800mM2)

        val shops = assertNotNull(report.shopping.value)
        assertEquals(listOf("supermarket", "shopping centre"), shops.items.map { it.category })
        assertEquals(listOf("Near Supermarket", "Hamilton Mall"), shops.items.map { it.name })
    }

    @Test
    fun `the walking estimate is the straight line times 1_3 at 80 metres a minute, rounded`() {
        assertEquals(0, LotReportFactory.walkingMinutes(0))
        assertEquals(2, LotReportFactory.walkingMinutes(100))
        assertEquals(16, LotReportFactory.walkingMinutes(1000))
        assertEquals(1, LotReportFactory.walkingMinutes(40))
    }

    @Test
    fun `a missing kind is unknown with a reason, not a silent blank`() {
        val noBeach = LotReportFactory.build(lot, nearby.copy(nearest = nearby.nearest.filter { it.kind != "beach" }))
        assertTrue(noBeach.beach.isUnknown)
        assertEquals("No ocean beach found in the OpenStreetMap data loaded", noBeach.beach.note)
        assertEquals(day, noBeach.beach.asOf)
        assertFalse(noBeach.parks.isUnknown)
        val oneShop = LotReportFactory.build(lot, nearby.copy(nearest = nearby.nearest.filter { it.kind != "mall" }))
        assertEquals(listOf("supermarket"), oneShop.shopping.value?.items?.map { it.category })
        val noShops = LotReportFactory.build(lot, nearby.copy(nearest = emptyList()))
        listOf(noShops.beach, noShops.parks, noShops.shopping).forEach { assertTrue(it.isUnknown) }
    }

    private val loaded = LocalDate(2026, 10, 30)

    @Test
    fun `terrain becomes a height and a slope class with source, date and notes`() {
        val report = LotReportFactory.build(lot, terrain = LotTerrainData(LotTerrain(35.04, 12.04, 21.3, 418), loaded))
        assertEquals(35.04, report.elevationM.value)
        assertEquals(Confidence.MEDIUM, report.elevationM.confidence)
        assertEquals(LotReportFactory.DEM_SOURCE, report.elevationM.source)
        assertEquals(loaded, report.elevationM.asOf)
        assertEquals(LotReportFactory.ELEVATION_NOTE, report.elevationM.note)
        val slope = assertNotNull(report.slope.value)
        assertEquals(SlopeClass.MODERATE, slope.slopeClass)
        assertEquals(12.04, slope.meanPercent)
        assertEquals(21.3, slope.maxPercent)
        assertEquals(null, slope.fall, "which way the lot falls is not worked out")
        assertTrue("not worked out" in report.slope.note.orEmpty())
    }

    @Test
    fun `a suburb where more than half the lots read under 1 m is too flat, so the row says so with no confidence and a reason`() {
        fun hint(share: Double?, lots: Int) = LotReportFactory.build(
            lot, drainage = LotDrainageData(LotDrainage(0.45, 20.9), loaded, suburbUnderOneMetreShare = share, suburbLotsWithResult = lots),
        ).heightAboveDrainage
        val flat = hint(0.91, 1500)
        assertEquals(DrainageProximity.FLAT_AREA, flat.value?.proximity)
        assertEquals(Confidence.NONE, flat.confidence)
        val note = flat.note.orEmpty()
        assertTrue("91% of this suburb's lots read under 1 m" in note && "not a flood result" in note.lowercase() && "overland flow" in note, note)
        // The edge: exactly half is not "more than half"; a suburb with few lots is not judged.
        assertEquals(DrainageProximity.VERY_CLOSE, hint(LotReportFactory.FLAT_SUBURB_SHARE_ABOVE, 1500).value?.proximity)
        assertEquals(DrainageProximity.FLAT_AREA, hint(0.51, LotReportFactory.FLAT_SUBURB_MIN_LOTS).value?.proximity)
        assertEquals(DrainageProximity.VERY_CLOSE, hint(0.9, LotReportFactory.FLAT_SUBURB_MIN_LOTS - 1).value?.proximity)
        assertEquals(DrainageProximity.VERY_CLOSE, hint(null, 0).value?.proximity)
    }

    @Test
    fun `drainage becomes a hint with its class, low confidence and a note that it is not a flood result`() {
        val report = LotReportFactory.build(lot, drainage = LotDrainageData(LotDrainage(0.45, 20.9), loaded))
        val hint = assertNotNull(report.heightAboveDrainage.value)
        assertEquals(0.45, hint.heightAboveDrainageM)
        assertEquals(20.9, hint.streamCatchmentHa)
        assertEquals(DrainageProximity.VERY_CLOSE, hint.proximity)
        assertEquals(Confidence.LOW, report.heightAboveDrainage.confidence)
        assertEquals(LotReportFactory.DEM_SOURCE, report.heightAboveDrainage.source)
        assertEquals(loaded, report.heightAboveDrainage.asOf)
        assertTrue("not a flood result" in report.heightAboveDrainage.note.orEmpty())
        assertTrue(report.flood.isUnknown, "flood stays unknown: drainage is not a flood result")
        assertEquals(DrainageProximity.WELL_ABOVE, LotReportFactory.build(lot, drainage = LotDrainageData(LotDrainage(7.2, 150.0), loaded)).heightAboveDrainage.value?.proximity)
    }

    @Test
    fun `without a drainage load the hint is unknown and says so, and a lot with no result says why`() {
        val notLoaded = LotReportFactory.build(lot)
        assertTrue(notLoaded.heightAboveDrainage.isUnknown)
        assertEquals(LotReportFactory.DRAINAGE_NOT_LOADED_NOTE, notLoaded.heightAboveDrainage.note)
        val noResult = LotReportFactory.build(lot, drainage = LotDrainageData(null, loaded))
        assertTrue(noResult.heightAboveDrainage.isUnknown)
        assertEquals(LotReportFactory.DRAINAGE_NO_RESULT_NOTE, noResult.heightAboveDrainage.note)
        assertEquals(loaded, noResult.heightAboveDrainage.asOf)
    }

    private fun school(code: Int, name: String, label: String?, metres: Double, icsea: Int? = 1000) =
        NearSchool(SchoolRecord(code, name, label, icsea, 300, "https://x.schools.nsw.gov.au"), metres)

    @Test
    fun `the nearest primary and the nearest high school are shown, nearest first, with an estimated walk and ICSEA`() {
        val data = LotSchoolData(
            listOf(
                school(1, "Far Primary", "Primary School", 2_400.0),
                school(2, "Near High", "Secondary School", 900.0),
                school(3, "Near Primary", "Primary School", 600.0, icsea = 1051),
                school(4, "Central School", "Central/Community School", 300.0),
            ),
            loaded,
        )
        val report = LotReportFactory.build(lot, schools = data)
        val nearest = assertNotNull(report.schools.value).nearest
        assertEquals(listOf("Near Primary", "Near High"), nearest.map { it.school.name })
        assertEquals(listOf(600, 900), nearest.map { it.distanceM })
        assertEquals(LotReportFactory.walkingMinutes(600), nearest.first().walkingMinutes)
        assertEquals(1051, nearest.first().school.icsea)
        assertEquals(SchoolSector.PUBLIC, nearest.first().school.sector)
        assertEquals(Confidence.MEDIUM, report.schools.confidence)
        assertEquals(SuburbReportFactory.SCHOOLS_SOURCE, report.schools.source)
        assertEquals(loaded, report.schools.asOf)
        assertTrue("not catchments" in report.schools.note.orEmpty() && "Private and Catholic" in report.schools.note.orEmpty())
    }

    @Test
    fun `where there is no primary or high school the nearest two of any kind are shown, and no candidates is unknown with a reason`() {
        val onlyCombined = LotSchoolData(
            listOf(school(1, "A", "Central/Community School", 500.0), school(2, "B", null, 800.0), school(3, "C", "Special School", 900.0)),
            loaded,
        )
        assertEquals(listOf("A", "B"), assertNotNull(LotReportFactory.build(lot, schools = onlyCombined).schools.value).nearest.map { it.school.name })
        val none = LotReportFactory.build(lot, schools = LotSchoolData(emptyList(), loaded)).schools
        assertTrue(none.isUnknown)
        assertEquals(LotReportFactory.LOT_SCHOOLS_NONE_NOTE, none.note)
        assertEquals(loaded, none.asOf)
    }

    private fun zone(code: Int, level: String, type: String, name: String, years: String?, school: SchoolRecord? = null, year: Int? = 2026) =
        CatchmentZone(code, level, type, name, years, year, school)

    @Test
    fun `the zoned schools come first, and a nearest school is kept only for a level with no zone, labelled as not zoned`() {
        val nearby = LotSchoolData(
            listOf(school(1, "Near Primary", "Primary School", 600.0), school(2, "Near High", "Secondary School", 900.0)),
            loaded,
        )
        val both = LotCatchmentData(
            listOf(
                zone(20, "HIGH", "HIGH_COED", "Newcastle HS", "7-12", SchoolRecord(20, "Newcastle High School", "Secondary School", 1030, 800, "https://nh.schools.nsw.gov.au")),
                zone(10, "PRIMARY", "PRIMARY", "Hamilton PS", "K-6", SchoolRecord(10, "Hamilton Public School", "Primary School", 1051, 412, null, "Hamilton")),
            ),
        )
        val report = LotReportFactory.build(lot, schools = nearby, catchments = both)
        val info = assertNotNull(report.schools.value)
        assertEquals(listOf("Hamilton Public School", "Newcastle High School"), info.zoned.map { it.name }, "primary first, then high")
        assertEquals(listOf("primary, K-6", "high, 7-12"), info.zoned.map { it.levelLabel })
        assertEquals(listOf(SchoolLevel.PRIMARY, SchoolLevel.HIGH), info.zoned.map { it.level })
        assertEquals(1051, info.zoned.first().icsea)
        assertEquals(listOf("Hamilton", null), info.zoned.map { it.locality }, "the address locality where the school data has one")
        assertTrue(info.zonesChecked)
        assertTrue(info.nearest.isEmpty(), "both levels have a zone, so no nearest school is added")
        assertEquals(LotReportFactory.CATCHMENTS_SOURCE, report.schools.source)
        val note = report.schools.note.orEmpty()
        assertTrue("does not guarantee a place" in note && "Department of Education" in note && "2026 enrolments" in note, note)

        // Only a primary zone: the nearest high school stays, as not zoned.
        val primaryOnly = LotCatchmentData(listOf(zone(10, "PRIMARY", "PRIMARY", "Hamilton PS", "K-6")))
        val partial = assertNotNull(LotReportFactory.build(lot, schools = nearby, catchments = primaryOnly).schools.value)
        assertEquals(listOf("Hamilton PS"), partial.zoned.map { it.name }, "the Department's short name is used where the school is not in the schools data")
        assertEquals(listOf("Near High"), partial.nearest.map { it.school.name })
    }

    @Test
    fun `a lot in no zone falls back to the nearest schools, and nothing at all is unknown with a reason`() {
        val nearby = LotSchoolData(listOf(school(1, "Near Primary", "Primary School", 600.0)), loaded)
        val none = LotCatchmentData(emptyList())
        val report = LotReportFactory.build(lot, schools = nearby, catchments = none)
        val info = assertNotNull(report.schools.value)
        assertTrue(info.zoned.isEmpty() && info.zonesChecked)
        assertEquals(listOf("Near Primary"), info.nearest.map { it.school.name })
        val nothing = LotReportFactory.build(lot, catchments = none).schools
        assertTrue(nothing.isUnknown)
        assertEquals(LotReportFactory.LOT_ZONED_NONE_NOTE, nothing.note)
        // Without catchments the row is as before (ADR 0045).
        assertEquals(false, assertNotNull(LotReportFactory.build(lot, schools = nearby).schools.value).zonesChecked)
    }

    @Test
    fun `zone kinds read in plain words`() {
        assertEquals("infants", LotReportFactory.zoneKind("INFANTS"))
        assertEquals("high, girls", LotReportFactory.zoneKind("HIGH_GIRLS"))
        assertEquals("central school, high years", LotReportFactory.zoneKind("CENTRAL_HIGH"))
        assertEquals("something new", LotReportFactory.zoneKind("SOMETHING_NEW"))
    }

    @Test
    fun `a school's website is kept only when it is a web address`() {
        val data = LotSchoolData(listOf(NearSchool(SchoolRecord(1, "P", "Primary School", 1000, null, "javascript:alert(1)"), 500.0)), loaded)
        assertEquals(null, assertNotNull(LotReportFactory.build(lot, schools = data).schools.value).nearest.single().school.website)
    }

    @Test
    fun `without a DEM load the height and slope are unknown and say so`() {
        val report = LotReportFactory.build(lot)
        assertTrue(report.elevationM.isUnknown && report.slope.isUnknown)
        assertEquals(LotReportFactory.DEM_NOT_LOADED_NOTE, report.elevationM.note)
        assertEquals(LotReportFactory.DEM_NOT_LOADED_NOTE, report.slope.note)
    }

    @Test
    fun `a lot outside the DEM is unknown with a reason, a lot on tile edges keeps its height only`() {
        val outside = LotReportFactory.build(lot, terrain = LotTerrainData(null, loaded))
        assertTrue(outside.elevationM.isUnknown && outside.slope.isUnknown)
        assertEquals(LotReportFactory.DEM_NO_COVERAGE_NOTE, outside.elevationM.note)
        assertEquals(loaded, outside.elevationM.asOf)
        val edge = LotReportFactory.build(lot, terrain = LotTerrainData(LotTerrain(12.0, null, null, 1), loaded))
        assertEquals(12.0, edge.elevationM.value)
        assertTrue(edge.slope.isUnknown)
    }

    @Test
    fun `a flat lot is flat and a maximum below the mean is lifted to it`() {
        val flat = assertNotNull(LotReportFactory.build(lot, terrain = LotTerrainData(LotTerrain(5.0, 0.5, 0.4, 20), loaded)).slope.value)
        assertEquals(SlopeClass.FLAT, flat.slopeClass)
        assertEquals(0.5, flat.maxPercent, "the model needs max >= mean; float rounding must not break a report")
    }

    @Test
    fun `a lot with no address and no label still reports`() {
        val report = LotReportFactory.build(lot.copy(parcelLabel = null, addresses = emptyList()))
        assertEquals(emptyList(), report.addresses)
        assertEquals(null, report.parcelLabel)
    }

    @Test
    fun `the report round trips through the API JSON`() {
        val report = LotReportFactory.build(lot, nearby)
        assertEquals(report, ApiJson.decodeFromString<LotReport>(ApiJson.encodeToString(report)))
    }
}
