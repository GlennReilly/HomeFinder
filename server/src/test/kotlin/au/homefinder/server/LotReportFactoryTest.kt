package au.homefinder.server

import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.Confidence
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
        val others = listOf(report.wallMaterial, report.backyardAspect, report.flood, report.estimatedPriceAud, report.schools, report.transport)
        assertEquals(6, others.size)
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
