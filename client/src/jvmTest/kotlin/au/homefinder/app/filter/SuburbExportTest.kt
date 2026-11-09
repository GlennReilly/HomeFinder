package au.homefinder.app.filter

import au.homefinder.app.selection.CardLayout
import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.model.FilterProvenance
import au.homefinder.shared.model.SuburbFilterValue
import au.homefinder.shared.model.SuburbFiltersResponse
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SuburbExportTest {
    private val provenance = FilterProvenance("Test", LocalDate(2026, 10, 2))
    private val data = FilterData.from(
        SuburbFiltersResponse(
            listOf(
                SuburbFilterValue("9002", rentedShare = 0.20, name = "MAYFIELD", zonedNaplan = 5, violentPer1000 = 31.0),
                SuburbFilterValue("9001", rentedShare = 0.45, name = "HAMILTON", zonedNaplan = -12, violentPer1000 = 4.2),
                SuburbFilterValue("9003", name = "WICKHAM"),
            ),
            "Test schools", LocalDate(2026, 10, 2),
            rented = provenance, naplan = provenance, crime = provenance,
        ),
    )

    private fun result(vararg limits: Pair<FilterCriterion, Double>) = data.apply(FilterSelection(limits.toMap()))

    @Test
    fun `the export is offered only with a filter on and between one and the cap of suburbs shaded`() {
        assertEquals(ExportAvailability.NoFilter, SuburbExport.availability(null))
        assertEquals(ExportAvailability.NoFilter, SuburbExport.availability(result()))
        assertEquals(ExportAvailability.NoMatch, SuburbExport.availability(result(FilterCriterion.RENTED to 10.0)))
        assertEquals(ExportAvailability.Ready(2), SuburbExport.availability(result(FilterCriterion.RENTED to 50.0)))
        val many = FilterResult(FilterSelection(mapOf(FilterCriterion.RENTED to 1.0)), (0..ApiContract.MAX_EXPORT_SUBURBS).map { "s$it" }.toSet(), 999, emptyMap())
        assertEquals(ExportAvailability.TooMany(ApiContract.MAX_EXPORT_SUBURBS + 1), SuburbExport.availability(many))
    }

    @Test
    fun `the button counts the suburbs, and the hint says why it is off`() {
        assertEquals("Export PDF (12 suburbs)", SuburbExport.buttonText(ExportAvailability.Ready(12)))
        assertEquals("Export PDF (1 suburb)", SuburbExport.buttonText(ExportAvailability.Ready(1)))
        assertEquals("Export PDF", SuburbExport.buttonText(ExportAvailability.NoFilter))
        assertEquals(SuburbExport.NO_FILTER_HINT, SuburbExport.hintText(ExportAvailability.NoFilter))
        assertEquals(SuburbExport.NO_MATCH_HINT, SuburbExport.hintText(ExportAvailability.NoMatch))
        assertEquals(
            "250 suburbs match: narrow the filters to ${ApiContract.MAX_EXPORT_SUBURBS} or fewer to export them as a PDF.",
            SuburbExport.hintText(ExportAvailability.TooMany(250)),
        )
        assertEquals("Making the PDF of 2 suburbs…", SuburbExport.exportingText(2))
        assertEquals("Downloaded x.pdf (1 suburb).", SuburbExport.doneText("x.pdf", 1))
        assertEquals("Could not make the PDF: timed out.", SuburbExport.failedText("timed out"))
    }

    @Test
    fun `the filter lines read as the panel does, and say the NAPLAN figures are your own`() {
        val lines = SuburbExport.filterLines(data, FilterSelection(mapOf(FilterCriterion.NAPLAN to -5.0, FilterCriterion.RENTED to 45.0, FilterCriterion.VIOLENT to 10.0)))
        assertEquals(
            listOf(
                "Homes rented: At most 45%",
                "Violent crime: At most 10 per 1,000",
                "Zoned schools' NAPLAN: At least -5${SuburbExport.NAPLAN_OWN_FIGURES}",
            ),
            lines,
        )
    }

    @Test
    fun `the request lists the shaded suburbs by name with the filters and the card layout`() {
        val layout = CardLayout(order = listOf("Parks"), hidden = setOf("Shopping"))
        val request = assertNotNull(SuburbExport.request(result(FilterCriterion.RENTED to 50.0), data, layout))
        assertEquals(listOf("9001", "9002"), request.suburbIds)
        assertEquals(listOf("Homes rented: At most 50%"), request.filters)
        assertEquals(layout, CardLayout.decode(request.cardLayout))
        assertNull(SuburbExport.request(result(), data, layout))
        assertNull(SuburbExport.request(result(FilterCriterion.RENTED to 10.0), data, layout))
    }
}
