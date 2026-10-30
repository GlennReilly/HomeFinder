package au.homefinder.server

import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.BlockSizeSpread
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.HomelyRating
import au.homefinder.shared.model.SuburbBeachDistance
import au.homefinder.shared.model.SuburbShopping
import au.homefinder.shared.model.SuburbReport
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuburbReportFactoryTest {
    private val hamilton = SuburbRecord("9001", "HAMILTON", "2303", 3.512)

    @Test
    fun `name postcode and area come from the boundary data`() {
        val report = SuburbReportFactory.build(hamilton)
        assertEquals("9001", report.suburbId)
        assertEquals("HAMILTON", report.name)
        assertEquals("2303", report.postcode)
        assertEquals(3.512, report.areaKm2.value)
        assertEquals(Confidence.HIGH, report.areaKm2.confidence)
        assertEquals(SuburbReportFactory.BOUNDARY_SOURCE, report.areaKm2.source)
    }

    @Test
    fun `without statistics every statistic is explicitly unknown with its source and a note, never a silent default`() {
        val report = SuburbReportFactory.build(hamilton)
        val salesStats = listOf(report.medianHousePriceAud, report.medianUnitPriceAud, report.priceChange12MonthsPercent, report.salesCount)
        salesStats.forEach {
            assertTrue(it.isUnknown)
            assertEquals(SuburbReportFactory.SALES_NOT_LOADED_NOTE, it.note)
            assertEquals(SuburbReportFactory.SALES_SOURCE, it.source)
        }
        val osm = listOf(report.beach, report.parkAreaShare, report.shopping)
        osm.forEach {
            assertTrue(it.isUnknown)
            assertEquals(LotReportFactory.OSM_NOT_LOADED_NOTE, it.note)
            assertEquals(LotReportFactory.OSM_SOURCE, it.source)
        }
        val others = listOf(report.blockSize, report.floodShare, report.brickShare, report.schools, report.transport)
        assertEquals(5, others.size)
        (salesStats + osm + others).forEach {
            assertTrue(it.isUnknown)
            assertEquals(Confidence.NONE, it.confidence)
            assertTrue(it.source.isNotBlank())
        }
        others.forEach { assertEquals(SuburbReportFactory.NOT_COMPUTED_NOTE, it.note) }
    }

    private val day = LocalDate(2026, 6, 1)
    private val full = SuburbStats(
        asOf = day, houseSales = 60, houseMedianAud = 1_050_000, unitSales = 20, unitMedianAud = 640_000, housePrevSales = 40,
        houseChangePercent = 4.2, blockLots = 300, blockMedianM2 = 560.0, blockP10M2 = 320.0, blockP90M2 = 810.0,
    )

    @Test
    fun `statistics become known values with source, as-of date and a note saying what they rest on`() {
        val report = SuburbReportFactory.build(hamilton, full)
        assertEquals(1_050_000L, report.medianHousePriceAud.value)
        assertEquals(640_000L, report.medianUnitPriceAud.value)
        assertEquals(4.2, report.priceChange12MonthsPercent.value)
        assertEquals(80, report.salesCount.value)
        assertEquals(BlockSizeSpread(560.0, 320.0, 810.0), report.blockSize.value)
        listOf(report.medianHousePriceAud, report.medianUnitPriceAud, report.priceChange12MonthsPercent, report.salesCount).forEach {
            assertEquals(SuburbReportFactory.SALES_SOURCE, it.source)
            assertEquals(day, it.asOf)
            assertTrue(it.note.orEmpty().isNotBlank())
        }
        assertEquals(SuburbReportFactory.CADASTRE_SOURCE, report.blockSize.source)
        assertTrue("60 house sales" in report.medianHousePriceAud.note.orEmpty())
        assertTrue("Valuer General weeks late" in report.salesCount.note.orEmpty())
    }

    @Test
    fun `confidence follows the number of sales behind a value`() {
        fun houseConfidence(sales: Int) =
            SuburbReportFactory.build(hamilton, full.copy(houseSales = sales)).medianHousePriceAud.confidence
        assertEquals(Confidence.LOW, houseConfidence(5))
        assertEquals(Confidence.LOW, houseConfidence(SuburbReportFactory.LOW_CONFIDENCE_BELOW - 1))
        assertEquals(Confidence.MEDIUM, houseConfidence(SuburbReportFactory.LOW_CONFIDENCE_BELOW))
        assertEquals(Confidence.MEDIUM, houseConfidence(SuburbReportFactory.MEDIUM_CONFIDENCE_BELOW - 1))
        assertEquals(Confidence.HIGH, houseConfidence(SuburbReportFactory.MEDIUM_CONFIDENCE_BELOW))
    }

    @Test
    fun `price change takes its confidence from the thinner of the two periods`() {
        val thin = SuburbReportFactory.build(hamilton, full.copy(houseSales = 200, housePrevSales = 8)).priceChange12MonthsPercent
        assertEquals(Confidence.LOW, thin.confidence)
        assertTrue("200 house sales" in thin.note.orEmpty() && "8 in the 12 months before" in thin.note.orEmpty())
    }

    @Test
    fun `a median with too few sales stays unknown and says how many there were`() {
        val report = SuburbReportFactory.build(hamilton, full.copy(houseSales = 3, houseMedianAud = null, houseChangePercent = null))
        val house = report.medianHousePriceAud
        assertTrue(house.isUnknown)
        assertEquals(Confidence.NONE, house.confidence)
        assertEquals(day, house.asOf)
        assertEquals("Only 3 house sales in the 12 months to 2026-06-01, too few for a median", house.note)
        assertTrue(report.priceChange12MonthsPercent.isUnknown)
        assertEquals(63 + 17, SuburbReportFactory.build(hamilton, full.copy(houseSales = 63, unitSales = 17)).salesCount.value)
    }

    @Test
    fun `no sales at all in the period is said plainly`() {
        val report = SuburbReportFactory.build(hamilton, full.copy(unitSales = 0, unitMedianAud = null))
        assertEquals("No unit sales in the 12 months to 2026-06-01", report.medianUnitPriceAud.note)
    }

    @Test
    fun `statistics without a sales date mean the sales were never loaded`() {
        val report = SuburbReportFactory.build(hamilton, full.copy(asOf = null, houseSales = 0, houseMedianAud = null, unitSales = 0, unitMedianAud = null, houseChangePercent = null))
        listOf(report.medianHousePriceAud, report.medianUnitPriceAud, report.priceChange12MonthsPercent, report.salesCount).forEach {
            assertEquals(SuburbReportFactory.SALES_NOT_LOADED_NOTE, it.note)
        }
        // Block size does not depend on the sales files.
        assertEquals(560.0, report.blockSize.value?.medianM2)
    }

    @Test
    fun `block size is unknown when no lots are linked and withheld when too few`() {
        val none = SuburbReportFactory.build(hamilton, full.copy(blockLots = 0, blockMedianM2 = null, blockP10M2 = null, blockP90M2 = null)).blockSize
        assertEquals(SuburbReportFactory.NOT_COMPUTED_NOTE, none.note)
        val few = SuburbReportFactory.build(hamilton, full.copy(blockLots = 4, blockMedianM2 = null, blockP10M2 = null, blockP90M2 = null)).blockSize
        assertTrue(few.isUnknown)
        assertEquals("Only 4 lots with an address, too few for a spread", few.note)
    }

    private val nearby = SuburbAmenity(
        beachFromCentreM = 1794.4, beachFromEdgeM = 860.2, beachName = "Ocean Beach", suburbAreaM2 = 4_000_000.0, parkAreaM2 = 50_000.0,
        shoppingCentres = 1, supermarkets = 3, osmAsOf = day,
    )

    @Test
    fun `the OpenStreetMap roll-up becomes beach distances, a park share and shop counts with source, date and notes`() {
        val report = SuburbReportFactory.build(hamilton, amenity = nearby)
        assertEquals(SuburbBeachDistance(1794, 860), report.beach.value)
        assertEquals(0.0125, report.parkAreaShare.value)
        assertEquals(SuburbShopping(1, 3), report.shopping.value)
        listOf(report.beach, report.parkAreaShare, report.shopping).forEach {
            assertEquals(Confidence.MEDIUM, it.confidence)
            assertEquals(LotReportFactory.OSM_SOURCE, it.source)
            assertEquals(day, it.asOf)
            assertTrue(it.note.orEmpty().isNotBlank())
        }
        assertTrue("Ocean Beach" in report.beach.note.orEmpty() && "nearest edge" in report.beach.note.orEmpty())
        assertTrue("2 km" in report.shopping.note.orEmpty())
    }

    @Test
    fun `no beach in the data is unknown with a reason, and a share cannot exceed all of the suburb`() {
        val noBeach = SuburbReportFactory.build(hamilton, amenity = nearby.copy(beachFromCentreM = null, beachFromEdgeM = null, beachName = null))
        assertTrue(noBeach.beach.isUnknown)
        assertEquals("No ocean beach found in the OpenStreetMap data loaded", noBeach.beach.note)
        assertEquals(day, noBeach.beach.asOf)
        assertEquals(1.0, SuburbReportFactory.build(hamilton, amenity = nearby.copy(parkAreaM2 = 9_000_000.0)).parkAreaShare.value)
        assertTrue(SuburbReportFactory.build(hamilton, amenity = nearby.copy(suburbAreaM2 = 0.0)).parkAreaShare.isUnknown)
    }

    @Test
    fun `no shops at all is shown as zero with low confidence, because an unmapped shop is more likely than none`() {
        val none = SuburbReportFactory.build(hamilton, amenity = nearby.copy(shoppingCentres = 0, supermarkets = 0)).shopping
        assertEquals(SuburbShopping(0, 0), none.value)
        assertEquals(Confidence.LOW, none.confidence)
        assertTrue("none are mapped" in none.note.orEmpty())
        assertEquals(Confidence.MEDIUM, SuburbReportFactory.build(hamilton, amenity = nearby.copy(shoppingCentres = 0, supermarkets = 1)).shopping.confidence)
    }

    @Test
    fun `a report with statistics round trips through the API JSON`() {
        val report = SuburbReportFactory.build(hamilton, full)
        assertEquals(report, ApiJson.decodeFromString<SuburbReport>(ApiJson.encodeToString(report)))
    }

    @Test
    fun `a suburb without label data has an unknown area and no postcode is allowed`() {
        val report = SuburbReportFactory.build(SuburbRecord("9002", "LAKE MACQUARIE", null, null))
        assertTrue(report.areaKm2.isUnknown)
        assertEquals(null, report.postcode)
    }

    @Test
    fun `the report round trips through the API JSON`() {
        val report = SuburbReportFactory.build(hamilton)
        assertEquals(report, ApiJson.decodeFromString<SuburbReport>(ApiJson.encodeToString(report)))
    }

    @Test
    fun `a record converts to a reference`() {
        val ref = hamilton.toRef()
        assertEquals("9001", ref.suburbId)
        assertEquals("HAMILTON", ref.name)
        assertEquals("2303", ref.postcode)
        assertFalse(ref.name.isEmpty())
    }

    @Test
    fun `a rating you entered becomes a known value with its source, date and an honest note`() {
        val report = SuburbReportFactory.build(hamilton, rating = StoredRating(HomelyRating(2.1, 7), day))
        val rating = report.homelyRating
        assertEquals(HomelyRating(2.1, 7), rating.value)
        assertEquals(HomelyRating.SOURCE, rating.source)
        assertEquals(day, rating.asOf)
        assertEquals(Confidence.LOW, rating.confidence)
        assertTrue("typed in by you" in rating.note.orEmpty() && "not fetched from Homely" in rating.note.orEmpty() && "7 reviews" in rating.note.orEmpty())
    }

    @Test
    fun `confidence follows the number of reviews you noted, and none noted counts as low`() {
        fun confidence(reviews: Int?) = SuburbReportFactory.build(hamilton, rating = StoredRating(HomelyRating(4.0, reviews), day)).homelyRating.confidence
        assertEquals(Confidence.LOW, confidence(null))
        assertEquals(Confidence.LOW, confidence(14))
        assertEquals(Confidence.MEDIUM, confidence(15))
        assertEquals(Confidence.HIGH, confidence(50))
    }

    @Test
    fun `without a rating the field says not entered`() {
        val rating = SuburbReportFactory.build(hamilton).homelyRating
        assertTrue(rating.isUnknown)
        assertEquals(SuburbReportFactory.HOMELY_NOT_ENTERED_NOTE, rating.note)
        assertEquals(HomelyRating.SOURCE, rating.source)
    }

    @Test
    fun `a report from before ratings existed still decodes with the rating not entered`() {
        val json = ApiJson.encodeToString(SuburbReportFactory.build(hamilton))
        val old = ApiJson.parseToJsonElement(json).jsonObject.filterKeys { it != "homelyRating" }
        val decoded = ApiJson.decodeFromString<SuburbReport>(JsonObject(old).toString())
        assertTrue(decoded.homelyRating.isUnknown)
    }
}
