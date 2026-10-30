package au.homefinder.server

import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.LotReport
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `every other factor is explicitly unknown with its source and a note`() {
        val report = LotReportFactory.build(lot)
        val others = listOf(
            report.wallMaterial, report.backyardAspect, report.flood, report.slope, report.estimatedPriceAud, report.beach,
            report.schools, report.parks, report.shopping, report.transport,
        )
        assertEquals(10, others.size)
        others.forEach {
            assertTrue(it.isUnknown)
            assertEquals(Confidence.NONE, it.confidence)
            assertEquals(LotReportFactory.NOT_COMPUTED_NOTE, it.note)
            assertTrue(it.source.isNotBlank())
        }
    }

    @Test
    fun `a lot with no address and no label still reports`() {
        val report = LotReportFactory.build(lot.copy(parcelLabel = null, addresses = emptyList()))
        assertEquals(emptyList(), report.addresses)
        assertEquals(null, report.parcelLabel)
    }

    @Test
    fun `the report round trips through the API JSON`() {
        val report = LotReportFactory.build(lot)
        assertEquals(report, ApiJson.decodeFromString<LotReport>(ApiJson.encodeToString(report)))
    }
}
