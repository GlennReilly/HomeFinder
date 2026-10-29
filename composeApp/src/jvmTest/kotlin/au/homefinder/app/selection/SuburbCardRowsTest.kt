package au.homefinder.app.selection

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.BlockSizeSpread
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.School
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolSector
import au.homefinder.shared.model.SuburbBeachDistance
import au.homefinder.shared.model.SuburbBrickShare
import au.homefinder.shared.model.SuburbFloodShare
import au.homefinder.shared.model.SuburbReport
import au.homefinder.shared.model.SuburbSchools
import au.homefinder.shared.model.SuburbShopping
import au.homefinder.shared.model.SuburbTransport
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuburbCardRowsTest {
    private val day = LocalDate(2026, 10, 29)
    private fun <T : Any> known(value: T) = Attribute(value, Confidence.MEDIUM, "Some source", day)
    private fun <T> unknown(source: String) = Attribute.unknown<T>(source, note = "Not computed yet")

    private val school = School("1", "Hamilton Public", SchoolSector.PUBLIC, SchoolLevel.PRIMARY)

    private val allUnknown = SuburbReport(
        "9001", "HAMILTON", "2303", known(4.894), unknown("A"), unknown("B"), unknown("C"), unknown("D"), unknown("E"), unknown("F"),
        unknown("G"), unknown("H"), unknown("I"), unknown("J"), unknown("K"), unknown("L"),
    )

    private val allKnown = SuburbReport(
        "9001", "HAMILTON", "2303", known(4.894), known(1_050_000L), known(640_000L), known(4.2), known(1_187),
        known(BlockSizeSpread(560.0, 320.0, 810.0)), known(SuburbFloodShare(0.18, 0.9)), known(SuburbBrickShare(0.42, 0.3)),
        known(SuburbBeachDistance(3500, 2400)), known(SuburbSchools(listOf(school), listOf(school, school))), known(0.07),
        known(SuburbShopping(2, 5)), known(SuburbTransport(2, 0, 0, 96, 8.0)),
    )

    @Test
    fun `the rows follow the spec order`() {
        assertEquals(
            listOf("Area", "Median house price", "Median unit price", "Price change, 12 months", "Sales behind the prices", "Typical block size",
                "Flood", "Likely brick", "Distance to the beach", "Schools", "Parks", "Shopping", "Transport"),
            SuburbCardRows.of(allUnknown).map { it.label },
        )
    }

    @Test
    fun `area is known and every statistic is shown as unknown with its source and note`() {
        val rows = SuburbCardRows.of(allUnknown)
        assertEquals("4.89 km²", rows.first().value)
        rows.drop(1).forEach {
            assertTrue(it.isUnknown, it.label)
            assertEquals(Confidence.NONE, it.confidence, it.label)
            assertEquals("Not computed yet", it.note, it.label)
        }
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"), rows.drop(1).map { it.source })
    }

    @Test
    fun `known values are formatted for reading`() {
        val rows = SuburbCardRows.of(allKnown).associate { it.label to it.value }
        assertEquals("\$1,050,000", rows["Median house price"])
        assertEquals("\$640,000", rows["Median unit price"])
        assertEquals("+4.2%", rows["Price change, 12 months"])
        assertEquals("1,187", rows["Sales behind the prices"])
        assertEquals("560 m² median (320 to 810 m²)", rows["Typical block size"])
        assertEquals("18% of lots touch a mapped flood extent; 90% of the suburb has flood mapping", rows["Flood"])
        assertEquals("42% of lots are likely brick; 30% have enough evidence to say", rows["Likely brick"])
        assertEquals("3.5 km from the centre, 2.4 km from the nearest edge", rows["Distance to the beach"])
        assertEquals("1 public schools inside; 2 catchments cover it", rows["Schools"])
        assertEquals("7% of the suburb is park", rows["Parks"])
        assertEquals("2 shopping centres and 5 supermarkets, inside or within 2 km", rows["Shopping"])
        assertEquals("2 train, 0 light rail, 0 ferry, 96 bus stops; best stop 8 an hour at peak", rows["Transport"])
    }

    @Test
    fun `provenance travels with the row`() {
        val area = SuburbCardRows.of(allKnown).first()
        assertEquals("Some source", area.source)
        assertEquals("2026-10-29", area.asOf)
        assertEquals(Confidence.MEDIUM, area.confidence)
    }

    @Test
    fun `transport without a peak frequency leaves it out`() {
        val report = allKnown.copy(transport = known(SuburbTransport(0, 0, 0, 12, null)))
        assertEquals("0 train, 0 light rail, 0 ferry, 12 bus stops", SuburbCardRows.of(report).last().value)
    }

    @Test
    fun `number formats round and group correctly`() {
        assertEquals("1,234,567", Formats.grouped(1_234_567))
        assertEquals("999", Formats.grouped(999))
        assertEquals("-12,345", Formats.grouped(-12_345))
        assertEquals("0", Formats.grouped(0))
        assertEquals("4.89", Formats.decimal(4.894, 2))
        assertEquals("4.90", Formats.decimal(4.896, 2))
        assertEquals("-0.5", Formats.decimal(-0.5, 1))
        assertEquals("0.0", Formats.decimal(-0.04, 1))
        assertEquals("3", Formats.decimal(2.6, 0))
        assertEquals("+4.2%", Formats.signedPercent(4.2))
        assertEquals("-1.5%", Formats.signedPercent(-1.5))
        assertEquals("0.0%", Formats.signedPercent(0.0))
        assertEquals("18%", Formats.share(0.18))
        assertEquals("100%", Formats.share(1.0))
        assertEquals("0%", Formats.share(0.0))
        assertEquals("0.3 km", Formats.kilometres(250))
        assertEquals("12.0 km", Formats.kilometres(12_000))
    }
}
