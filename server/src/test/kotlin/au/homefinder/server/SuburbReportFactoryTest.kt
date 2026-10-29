package au.homefinder.server

import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.SuburbReport
import kotlinx.serialization.encodeToString
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
    fun `every statistic is explicitly unknown with its source and a note, never a silent default`() {
        val report = SuburbReportFactory.build(hamilton)
        val stats = listOf(
            report.medianHousePriceAud, report.medianUnitPriceAud, report.priceChange12MonthsPercent, report.salesCount,
            report.blockSize, report.floodShare, report.brickShare, report.beach, report.schools, report.parkAreaShare,
            report.shopping, report.transport,
        )
        assertEquals(12, stats.size)
        stats.forEach {
            assertTrue(it.isUnknown)
            assertEquals(Confidence.NONE, it.confidence)
            assertEquals(SuburbReportFactory.NOT_COMPUTED_NOTE, it.note)
            assertTrue(it.source.isNotBlank())
        }
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
}
