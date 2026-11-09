package au.homefinder.app.selection

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.BlockSizeSpread
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.CrimeRate
import au.homefinder.shared.model.DwellingMix
import au.homefinder.shared.model.School
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolSector
import au.homefinder.shared.model.SocioEconomic
import au.homefinder.shared.model.SuburbBeachDistance
import au.homefinder.shared.model.SuburbBrickShare
import au.homefinder.shared.model.SuburbFloodShare
import au.homefinder.shared.model.SuburbReport
import au.homefinder.shared.model.SuburbSchools
import au.homefinder.shared.model.SuburbShopping
import au.homefinder.shared.model.SuburbTransport
import au.homefinder.shared.model.SuburbZone
import au.homefinder.shared.model.SuburbZonedSchools
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    ).copy(rentedShare = known(0.452), dwellingMix = known(DwellingMix(0.58, 0.25, 0.15)))

    private val hamiltonPs = School("10", "Hamilton Public School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, icsea = 1041, levelLabel = "primary, K-6")
    private val girlsHigh = School("20", "Newcastle Girls High", SchoolSector.PUBLIC, SchoolLevel.HIGH, icsea = null, levelLabel = "high, girls, 7-12")

    @Test
    fun `the Schools row is the zoned schools and names each zone and its kind, and each zone's first line gives its years and share, ICSEA its own line`() {
        val zoned = SuburbZonedSchools(listOf(SuburbZone(hamiltonPs, 0.82), SuburbZone(girlsHigh, 0.255)), 2026)
        val row = SuburbCardRows.of(allKnown.copy(zonedSchools = known(zoned))).first { it.label == SuburbCardRows.SCHOOLS_LABEL }
        assertEquals("Hamilton Public School (primary), Newcastle Girls High (high)", row.value)
        assertEquals("primary, K-6 · 82% of the suburb's lots", SuburbCardRows.zoneDetail(zoned.zones[0]))
        assertEquals("ICSEA 1,041", SuburbCardRows.icseaText(zoned.zones[0].school))
        assertEquals("high, girls, 7-12 · 26% of the suburb's lots", SuburbCardRows.zoneDetail(zoned.zones[1]))
        assertEquals(null, SuburbCardRows.icseaText(zoned.zones[1].school), "no ICSEA, no line")
        assertTrue(SuburbCardRows.of(allUnknown).first { it.label == SuburbCardRows.SCHOOLS_LABEL }.isUnknown)
    }

    @Test
    fun `the rows have no area and put the typical block size under the median house price`() {
        assertEquals(
            listOf("Median house price", "Typical block size", "Median unit price", "Price change, 12 months",
                "Socio-economic advantage", "Disadvantage (SEIFA)", "Economic resources (SEIFA)", "Education and occupation (SEIFA)", "Homes rented", "Houses and units", "Violent crime", "Break-ins, car and home theft", "Distance to the beach", "Schools", "Parks", "Shopping", "Transport"),
            SuburbCardRows.of(allUnknown).map { it.label },
        )
    }

    @Test
    fun `every statistic is shown as unknown with its source and note`() {
        val rows = SuburbCardRows.of(allUnknown)
        assertFalse(rows.any { it.label == "Area" || "km²" in it.value.orEmpty() })
        rows.forEach {
            assertTrue(it.isUnknown, it.label)
            assertEquals(Confidence.NONE, it.confidence, it.label)
            assertEquals("Not computed yet", it.note, it.label)
        }
        assertEquals(listOf("A", "E", "B", "C", "ABS SEIFA 2021", "ABS SEIFA 2021", "ABS SEIFA 2021", "ABS SEIFA 2021", "ABS 2021 Census", "ABS 2021 Census", "NSW BOCSAR", "NSW BOCSAR", "H", "NSW Department of Education", "J", "K", "L"), rows.map { it.source }, "each row keeps the source of its own factor")
    }

    @Test
    fun `the sales count sits in brackets after the price change, with its note after the change's own`() {
        val withNotes = allKnown.copy(
            priceChange12MonthsPercent = Attribute(4.2, Confidence.MEDIUM, "Sales", day, "House median price, from 40 and 38 house sales"),
            salesCount = Attribute(31, Confidence.MEDIUM, "Sales", day, "24 house and 7 unit sales in the 12 months to the latest sale on file."),
        )
        val row = SuburbCardRows.priceChange(withNotes)
        assertEquals("+4.2% (31 sales)", row.value)
        assertEquals("House median price, from 40 and 38 house sales. 24 house and 7 unit sales in the 12 months to the latest sale on file.", row.note)
        assertEquals("From 40 sales. 24 house and 7 unit sales.", SuburbCardRows.priceChange(withNotes.copy(priceChange12MonthsPercent = Attribute(4.2, Confidence.MEDIUM, "Sales", day, "From 40 sales."), salesCount = Attribute(31, Confidence.MEDIUM, "Sales", day, "24 house and 7 unit sales."))).note, "one full stop between the notes, not two")
        assertEquals(Confidence.MEDIUM, row.confidence, "the row keeps the price change's own confidence")
        assertEquals("+4.2% (1 sale)", SuburbCardRows.priceChange(withNotes.copy(salesCount = known(1))).value)
    }

    @Test
    fun `an unknown price change stays empty with its reason, and a missing count adds nothing`() {
        val reason = Attribute.unknown<Double>("Sales", day, "Too few house sales to compare two 12-month periods (2 and 8)")
        val unknownChange = SuburbCardRows.priceChange(allKnown.copy(priceChange12MonthsPercent = reason))
        assertEquals(null, unknownChange.value)
        assertEquals("Too few house sales to compare two 12-month periods (2 and 8)", unknownChange.note)
        assertEquals("+4.2%", SuburbCardRows.priceChange(allKnown.copy(salesCount = unknown("Sales"))).value)
    }

    @Test
    fun `known values are formatted for reading`() {
        val rows = SuburbCardRows.of(allKnown).associate { it.label to it.value }
        assertEquals("\$1,050,000", rows["Median house price"])
        assertEquals("\$640,000", rows["Median unit price"])
        assertEquals("+4.2% (1,187 sales)", rows["Price change, 12 months"])
        assertFalse("Sales behind the prices" in rows, "the count lives in the price change row now (ADR 0059)")
        assertEquals("560 m² median (320 to 810 m²)", rows["Typical block size"])
        assertEquals("45% of homes are rented", rows["Homes rented"])
        val other = SuburbCardRows.of(allKnown.copy(disadvantage = known(6), economicResources = known(3), educationOccupation = known(9))).associate { it.label to it.value }
        assertEquals("Decile 6 of 10 (10 is the least disadvantaged)", other["Disadvantage (SEIFA)"])
        assertEquals("Decile 3 of 10 (10 has the most resources)", other["Economic resources (SEIFA)"])
        assertEquals("Decile 9 of 10 (10 is the highest)", other["Education and occupation (SEIFA)"])
        val withRate = SuburbCardRows.of(allKnown.copy(violentCrime = known(CrimeRate(31, 24, 4.2, 7380)), propertyCrime = known(CrimeRate(1, 24))))
        assertEquals("4.2 per 1,000 residents (31 incidents in 24 months)", withRate.first { it.label == "Violent crime" }.value)
        assertEquals("1 incident in 24 months, no population to turn it into a rate", withRate.first { it.label == "Break-ins, car and home theft" }.value)
        val advantaged = SuburbCardRows.of(allKnown.copy(socioEconomic = known(SocioEconomic(8, 1035.7, 4614)))).first { it.label == "Socio-economic advantage" }
        assertEquals("Decile 8 of 10 (10 is the most advantaged)", advantaged.value)
        assertEquals("58% houses, 25% townhouses, 15% units", rows["Houses and units"])
        assertEquals("3.5 km from the centre, 2.4 km from the nearest edge", rows["Distance to the beach"])
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

    @Test
    fun `the Schools row ignores the schools inside the suburb and has no row of its own for the zones (ADR 0073)`() {
        val rows = SuburbCardRows.of(allKnown.copy(zonedSchools = known(SuburbZonedSchools(listOf(SuburbZone(hamiltonPs, 0.82))))))
        assertEquals(1, rows.count { it.label == "Schools" })
        assertFalse(rows.any { it.label == "Zoned schools" })
        assertEquals("Hamilton Public School (primary)", rows.first { it.label == "Schools" }.value)
        val none = SuburbCardRows.of(allKnown).first { it.label == "Schools" }
        assertTrue(none.isUnknown, "no zone data: Unknown, with the reason in the note")
        assertEquals("Not computed yet", none.note)
    }
}
