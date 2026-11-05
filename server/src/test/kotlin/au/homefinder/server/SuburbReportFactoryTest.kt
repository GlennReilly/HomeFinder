package au.homefinder.server

import au.homefinder.shared.model.CrimeRate
import au.homefinder.shared.model.SocioEconomic
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.BlockSizeSpread
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.HomelyRating
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolSector
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        assertTrue(report.schools.isUnknown)
        assertEquals(SuburbReportFactory.SCHOOLS_NOT_LOADED_NOTE, report.schools.note)
        val others = listOf(report.blockSize, report.floodShare, report.brickShare, report.transport)
        assertEquals(4, others.size)
        (salesStats + osm + others + report.schools).forEach {
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
        assertTrue(SuburbReportFactory.LATE_SALES_NOTE in report.salesCount.note.orEmpty())
        assertTrue("12 months to the latest sale on file (" in report.salesCount.note.orEmpty(), "the date is the latest sale, not the download day")
        assertTrue(SuburbReportFactory.LATE_SALES_NOTE in report.medianHousePriceAud.note.orEmpty())
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
        assertEquals("Only 3 house sales in the 12 months to the latest sale on file (2026-06-01), too few for a median", house.note)
        assertTrue(report.priceChange12MonthsPercent.isUnknown)
        assertEquals(63 + 17, SuburbReportFactory.build(hamilton, full.copy(houseSales = 63, unitSales = 17)).salesCount.value)
    }

    @Test
    fun `no sales at all in the period is said plainly`() {
        val report = SuburbReportFactory.build(hamilton, full.copy(unitSales = 0, unitMedianAud = null))
        assertEquals("No unit sales in the 12 months to the latest sale on file (2026-06-01)", report.medianUnitPriceAud.note)
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
    fun `Census counts become a share rented and a dwelling mix with the Census date and the ABS attribution`() {
        val report = SuburbReportFactory.build(hamilton, census = SuburbCensus(tenureStated = 1000, rented = 450, houses = 500, semis = 250, flats = 240, otherDwellings = 10))
        assertEquals(0.45, report.rentedShare.value)
        assertEquals(Confidence.HIGH, report.rentedShare.confidence)
        assertEquals(SuburbReportFactory.CENSUS_SOURCE, report.rentedShare.source)
        assertEquals(LocalDate(2021, 8, 10), report.rentedShare.asOf, "Census night")
        val mix = assertNotNull(report.dwellingMix.value)
        assertEquals(listOf(0.5, 0.25, 0.24), listOf(mix.houseShare, mix.townhouseShare, mix.unitShare))
        listOf(report.rentedShare.note, report.dwellingMix.note).forEach {
            assertTrue(SuburbReportFactory.CENSUS_ATTRIBUTION in it.orEmpty(), "every derived figure carries the ABS attribution")
            assertTrue("randomly adjusts small counts" in it.orEmpty())
        }
    }

    @Test
    fun `a suburb with few homes has low confidence, and no Census data or no homes is unknown with a reason`() {
        val small = SuburbReportFactory.build(hamilton, census = SuburbCensus(tenureStated = 10, rented = 4, houses = 6, semis = 0, flats = 0, otherDwellings = 0))
        assertEquals(Confidence.LOW, small.rentedShare.confidence)
        assertEquals(Confidence.LOW, small.dwellingMix.confidence)
        val none = SuburbReportFactory.build(hamilton)
        assertTrue(none.rentedShare.isUnknown && none.dwellingMix.isUnknown)
        assertEquals(SuburbReportFactory.CENSUS_NOT_LOADED_NOTE, none.rentedShare.note)
        val empty = SuburbReportFactory.build(hamilton, census = SuburbCensus(0, 0, 0, 0, 0, 0))
        assertTrue(empty.rentedShare.isUnknown && empty.dwellingMix.isUnknown)
        assertEquals(LocalDate(2021, 8, 10), empty.rentedShare.asOf)
    }

    @Test
    fun `SEIFA becomes a decile with the score, population, Census date and ABS attribution, firmer for bigger suburbs`() {
        val report = SuburbReportFactory.build(hamilton, seifa = SuburbSeifa(irsadDecile = 8, irsadScore = 1035.7, population = 4614))
        assertEquals(SocioEconomic(8, 1035.7, 4614), report.socioEconomic.value)
        assertEquals(Confidence.HIGH, report.socioEconomic.confidence)
        assertEquals(LocalDate(2021, 8, 10), report.socioEconomic.asOf)
        assertEquals(SuburbReportFactory.SEIFA_SOURCE, report.socioEconomic.source)
        val note = assertNotNull(report.socioEconomic.note)
        assertTrue("4614 residents" in note && "not any one household" in note && "Based on Australian Bureau of Statistics data" in note, note)
        assertEquals(Confidence.MEDIUM, SuburbReportFactory.build(hamilton, seifa = SuburbSeifa(5, 990.0, 500)).socioEconomic.confidence)
        assertEquals(Confidence.LOW, SuburbReportFactory.build(hamilton, seifa = SuburbSeifa(1, 880.0, 33)).socioEconomic.confidence)
    }

    @Test
    fun `crime counts become a rate per 1,000 residents with the window, the BOCSAR source and the caveat`() {
        val report = SuburbReportFactory.build(hamilton, crime = SuburbCrime(violent = 31, property = 60, windowText = "Jul 2024 to Jun 2026", asOf = LocalDate(2026, 6, 30), population = 7380))
        val violent = assertNotNull(report.violentCrime.value)
        assertEquals(CrimeRate(31, 24, 31 * 1000.0 / 7380, 7380), violent)
        assertEquals(Confidence.HIGH, report.violentCrime.confidence)
        assertEquals(LocalDate(2026, 6, 30), report.violentCrime.asOf)
        assertEquals(SuburbReportFactory.CRIME_SOURCE, report.violentCrime.source)
        val note = assertNotNull(report.violentCrime.note)
        assertTrue("Jul 2024 to Jun 2026" in note && "counted where it happens" in note && "reporting and policing" in note && "7380 residents" in note, note)
        assertEquals(60 * 1000.0 / 7380, report.propertyCrime.value?.perThousand)
    }

    @Test
    fun `a small suburb has low confidence, one with no population has the count only, and no crime data is unknown with a reason`() {
        val small = SuburbReportFactory.build(hamilton, crime = SuburbCrime(3, 5, "w", LocalDate(2026, 6, 30), population = 200))
        assertEquals(Confidence.LOW, small.violentCrime.confidence)
        assertEquals(Confidence.MEDIUM, SuburbReportFactory.build(hamilton, crime = SuburbCrime(3, 5, "w", LocalDate(2026, 6, 30), 1_000)).violentCrime.confidence)
        val none = SuburbReportFactory.build(hamilton, crime = SuburbCrime(3, 5, "w", LocalDate(2026, 6, 30), population = null))
        assertEquals(CrimeRate(3, 24, null, null), none.violentCrime.value)
        assertEquals(Confidence.LOW, none.violentCrime.confidence)
        assertTrue("No population figure" in assertNotNull(none.violentCrime.note))
        val unloaded = SuburbReportFactory.build(hamilton)
        assertTrue(unloaded.violentCrime.isUnknown && unloaded.propertyCrime.isUnknown)
        assertEquals(SuburbReportFactory.CRIME_NOT_LOADED_NOTE, unloaded.violentCrime.note)
    }

    @Test
    fun `the other three SEIFA indexes are deciles with the same source, date and confidence, and unknown where the ABS gave no score`() {
        val report = SuburbReportFactory.build(hamilton, seifa = SuburbSeifa(8, 1035.7, 4614, irsdDecile = 6, ierDecile = null, ieoDecile = 9))
        assertEquals(6, report.disadvantage.value)
        assertEquals(9, report.educationOccupation.value)
        assertEquals(Confidence.HIGH, report.disadvantage.confidence)
        assertEquals(LocalDate(2021, 8, 10), report.disadvantage.asOf)
        assertTrue("4614 residents" in assertNotNull(report.disadvantage.note))
        assertTrue(report.economicResources.isUnknown)
        assertEquals(SuburbReportFactory.SEIFA_NO_SCORE_NOTE, report.economicResources.note)
        val none = SuburbReportFactory.build(hamilton)
        assertTrue(none.disadvantage.isUnknown && none.economicResources.isUnknown && none.educationOccupation.isUnknown)
        assertEquals(SuburbReportFactory.SEIFA_NOT_LOADED_NOTE, none.disadvantage.note)
        assertEquals(Confidence.LOW, SuburbReportFactory.build(hamilton, seifa = SuburbSeifa(1, 880.0, 33, irsdDecile = 1)).disadvantage.confidence)
    }

    private fun share(code: Int, level: String, share: Double, name: String = "School $code", icsea: Int? = 1000, type: String = if (level == "HIGH") "HIGH_COED" else "PRIMARY") =
        SuburbZoneShare(CatchmentZone(code, level, type, "$name (dept)", if (level == "HIGH") "7-12" else "K-6", 2026, SchoolRecord(code, name, "Primary School", icsea, 400, null)), share)

    @Test
    fun `zones covering a quarter of the suburb are listed per level, primary first and largest first, and a level with none that big lists its biggest`() {
        val listed = SuburbReportFactory.listedZones(
            listOf(
                share(20, "HIGH", 0.90), share(11, "PRIMARY", 0.30), share(10, "PRIMARY", 0.62), share(12, "PRIMARY", 0.07),
                share(30, "HIGH", 0.10), // below a quarter but the high level has a bigger one, so it is not listed
            ),
        )
        assertEquals(listOf(10, 11, 20), listed.map { it.zone.schoolCode })
        val split = SuburbReportFactory.listedZones(listOf(share(1, "PRIMARY", 0.20), share(2, "PRIMARY", 0.18), share(3, "HIGH", 0.24)))
        assertEquals(listOf(1, 3), split.map { it.zone.schoolCode }, "no zone reaches a quarter, so each level lists its biggest")
        assertEquals(listOf(5), SuburbReportFactory.listedZones(listOf(share(5, "PRIMARY", 0.25))).map { it.zone.schoolCode }, "exactly a quarter counts")
    }

    @Test
    fun `the zoned schools carry the school, the kind of zone, the share, the enrolment year and the reliance note`() {
        val report = SuburbReportFactory.build(hamilton, zones = SuburbZoneData(listOf(share(10, "PRIMARY", 0.82, "Hamilton Public School", 1041), share(20, "HIGH", 0.76, "Newcastle High School", null))))
        val zoned = assertNotNull(report.zonedSchools.value)
        assertEquals(2026, zoned.enrolmentYear)
        assertEquals(listOf("Hamilton Public School", "Newcastle High School"), zoned.zones.map { it.school.name })
        assertEquals(listOf(0.82, 0.76), zoned.zones.map { it.lotShare })
        assertEquals("primary, K-6", zoned.zones.first().school.levelLabel)
        assertEquals(1041, zoned.zones.first().school.icsea)
        assertNull(zoned.zones.last().school.icsea)
        assertEquals(Confidence.MEDIUM, report.zonedSchools.confidence)
        val note = assertNotNull(report.zonedSchools.note)
        assertTrue("guarantee a place" in note && "2026 enrolments" in note && "Based on NSW Department of Education data" in note, note)
    }

    @Test
    fun `without catchments the zoned row is unknown with a reason, and a suburb no zone covers is unknown with another`() {
        val none = SuburbReportFactory.build(hamilton).zonedSchools
        assertTrue(none.isUnknown)
        assertEquals(SuburbReportFactory.ZONED_NOT_LOADED_NOTE, none.note)
        val uncovered = SuburbReportFactory.build(hamilton, zones = SuburbZoneData(emptyList())).zonedSchools
        assertTrue(uncovered.isUnknown)
        assertEquals(SuburbReportFactory.ZONED_NONE_NOTE, uncovered.note)
    }

    @Test
    fun `without SEIFA data the socio-economic row is unknown with a reason`() {
        val none = SuburbReportFactory.build(hamilton).socioEconomic
        assertTrue(none.isUnknown)
        assertEquals(SuburbReportFactory.SEIFA_NOT_LOADED_NOTE, none.note)
    }

    @Test
    fun `a suburb with no mapped park is unknown with that reason, not zero percent`() {
        val none = SuburbReportFactory.build(hamilton, amenity = nearby.copy(parkAreaM2 = 0.0)).parkAreaShare
        assertTrue(none.isUnknown)
        assertEquals(SuburbReportFactory.NO_PARK_NOTE, none.note)
        assertEquals(day, none.asOf)
        assertEquals(0.0125, SuburbReportFactory.build(hamilton, amenity = nearby).parkAreaShare.value, "a suburb with parks keeps its share")
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

    private fun school(code: Int, name: String, icsea: Int?, enrolments: Int? = 300, website: String? = "https://x.example.nsw.edu.au", label: String? = "Primary School") =
        SchoolRecord(code, name, label, icsea, enrolments, website)

    @Test
    fun `public schools inside the suburb become schools with their ICSEA, source, date and the caveats`() {
        val data = SuburbSchoolData(listOf(school(1, "Hamilton Public School", 1051), school(2, "Hamilton High School", 1012, label = "Secondary School"), school(3, "Small School", null)), day)
        val schools = SuburbReportFactory.build(hamilton, schools = data).schools
        val value = assertNotNull(schools.value)
        assertEquals(listOf("Hamilton Public School", "Hamilton High School", "Small School"), value.publicSchoolsInside.map { it.name })
        assertEquals(listOf(SchoolLevel.PRIMARY, SchoolLevel.HIGH, SchoolLevel.PRIMARY), value.publicSchoolsInside.map { it.level })
        assertEquals(listOf(1051, 1012, null), value.publicSchoolsInside.map { it.icsea })
        assertTrue(value.publicSchoolsInside.all { it.sector == SchoolSector.PUBLIC })
        assertEquals(1032, value.averageIcsea, "the mean of the schools that have an ICSEA (1051 and 1012), rounded")
        assertEquals(emptyList(), value.catchments)
        assertEquals(SuburbReportFactory.SCHOOLS_SOURCE, schools.source)
        assertEquals(day, schools.asOf)
        assertEquals(Confidence.HIGH, schools.confidence)
        assertTrue("not how good the school is" in schools.note.orEmpty() && "public schools only" in schools.note.orEmpty().lowercase())
    }

    @Test
    fun `school kinds map to the three levels and a website can only be http or https`() {
        assertEquals(SchoolLevel.PRIMARY, SuburbReportFactory.schoolLevel("Infants School"))
        assertEquals(SchoolLevel.HIGH, SuburbReportFactory.schoolLevel("Secondary School"))
        assertEquals(SchoolLevel.COMBINED, SuburbReportFactory.schoolLevel("Central/Community School"))
        assertEquals(SchoolLevel.COMBINED, SuburbReportFactory.schoolLevel("Schools for Specific Purposes"))
        assertEquals(SchoolLevel.COMBINED, SuburbReportFactory.schoolLevel(null))
        assertEquals("https://a.example", SuburbReportFactory.safeWebsite(" https://a.example "))
        assertEquals("http://a.example", SuburbReportFactory.safeWebsite("http://a.example"))
        listOf("javascript:alert(1)", "data:text/html,x", "//a.example", "a.example", "", null).forEach { assertNull(SuburbReportFactory.safeWebsite(it), it.toString()) }
    }

    @Test
    fun `a suburb with no public school says so, no ICSEA gives no average, and no school data says it is not loaded`() {
        val none = SuburbReportFactory.build(hamilton, schools = SuburbSchoolData(emptyList(), day)).schools
        assertEquals(emptyList(), none.value?.publicSchoolsInside)
        assertNull(none.value?.averageIcsea)
        val unpublished = SuburbReportFactory.build(hamilton, schools = SuburbSchoolData(listOf(school(1, "A", null)), day)).schools
        assertNull(unpublished.value?.averageIcsea)
        assertEquals(1, unpublished.value?.publicSchoolsInside?.size)
        assertEquals(SuburbReportFactory.SCHOOLS_NOT_LOADED_NOTE, SuburbReportFactory.build(hamilton).schools.note)
    }

    @Test
    fun `a javascript address in the data never reaches the report`() {
        val schools = SuburbReportFactory.build(hamilton, schools = SuburbSchoolData(listOf(school(1, "A", 1000, website = "javascript:alert(1)")), day)).schools
        assertNull(schools.value?.publicSchoolsInside?.single()?.website)
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
