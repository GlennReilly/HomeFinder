package au.homefinder.server

import au.homefinder.app.selection.CardGroups
import au.homefinder.app.selection.CardLayout
import au.homefinder.app.selection.SuburbCardRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuburbExportContentTest {
    @Test
    fun `every row is listed under the card's groups in the default order, rows with no data saying so`() {
        val suburb = SuburbExportContent.suburb(ExportSamples.report(), CardLayout())
        assertEquals("HAMILTON", suburb.name)
        assertEquals("2303", suburb.postcode)
        assertEquals(CardGroups.SUBURB.map { it.title }, suburb.groups.map { it.title })
        val prices = suburb.groups.first().lines
        assertEquals(SuburbCardRows.MEDIAN_HOUSE_LABEL, prices.first().label)
        assertEquals("$900,000", prices.first().value)
        assertEquals("Test sales · as of 2026-10-09 · high confidence", prices.first().source)
        val transport = suburb.groups.flatMap { it.lines }.single { it.label == "Transport" }
        assertEquals(SuburbExportContent.NO_DATA, transport.value)
    }

    @Test
    fun `your layout orders the groups and rows, and rows you hid are left out`() {
        val layout = CardLayout(order = listOf("Shopping", "Parks"), hidden = setOf("Parks"), groupOrder = listOf("Location"))
        val suburb = SuburbExportContent.suburb(ExportSamples.report(), layout)
        assertEquals("Location", suburb.groups.first().title)
        val labels = suburb.groups.first().lines.map { it.label }
        assertEquals("Shopping", labels.first())
        assertTrue("Parks" !in labels, "a hidden row is not exported: $labels")
    }

    @Test
    fun `the Schools row lists each zoned school with its ICSEA and your NAPLAN figure, `() {
        val schools = SuburbExportContent.suburb(ExportSamples.report(naplan = -5), CardLayout()).groups.single { it.title == CardGroups.SCHOOLS_TITLE }.lines.single()
        assertNull(schools.value)
        assertEquals(
            listOf(
                "Hamilton South Public School: primary, K-6 · 82% of the suburb's lots",
                "  ICSEA 1,041",
                "  NAPLAN -5 vs national",
                "Newcastle High School: high, 7-12 · 76% of the suburb's lots",
                "  ICSEA 1,017",
            ),
            schools.subLines,
        )
        assertTrue(schools.source.orEmpty().contains("Your own figure, typed in."), schools.source)
    }

    @Test
    fun `the Homely score you entered closes the suburb as its own group`() {
        val suburb = SuburbExportContent.suburb(ExportSamples.report(rated = true), CardLayout())
        val homely = suburb.groups.last()
        assertEquals(SuburbExportContent.HOMELY_TITLE, homely.title)
        assertEquals("4.1 ★ (7 reviews)", homely.lines.single().value)
        assertTrue(SuburbExportContent.suburb(ExportSamples.report(), CardLayout()).groups.none { it.title == SuburbExportContent.HOMELY_TITLE })
    }
}
