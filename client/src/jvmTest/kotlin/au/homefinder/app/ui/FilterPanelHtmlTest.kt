package au.homefinder.app.ui

import au.homefinder.app.filter.FilterData
import au.homefinder.app.filter.FilterSelection
import au.homefinder.app.filter.IcseaRange
import au.homefinder.shared.model.SuburbFilterValue
import au.homefinder.shared.model.SuburbFiltersResponse
import kotlinx.datetime.LocalDate
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterPanelHtmlTest {
    private fun data(vararg values: Int?) = FilterData.from(
        SuburbFiltersResponse(values.mapIndexed { i, v -> SuburbFilterValue("s$i", v) }, "NSW schools", LocalDate(2026, 10, 2)),
    )

    private fun html(data: FilterData) = createHTML().div { filterPanel(data, data.apply(FilterSelection())) }

    @Test
    fun `the panel has a switch and two sliders spanning the data, in steps of ten`() {
        val out = html(data(1032, 961, null))
        assertTrue("""id="${FilterIds.ICSEA_ON}"""" in out)
        assertTrue("""id="${FilterIds.ICSEA_MIN}"""" in out && """id="${FilterIds.ICSEA_MAX}"""" in out)
        assertTrue("""min="960"""" in out && """max="1040"""" in out && """step="10"""" in out, out)
    }

    @Test
    fun `it says ICSEA is not school quality and gives the source and date`() {
        val out = html(data(1032))
        assertTrue("not a measure of school quality" in out)
        assertTrue("Source: NSW schools · as of 2026-10-02" in out, out)
    }

    @Test
    fun `without any ICSEA it says the schools are not loaded and has no sliders`() {
        val out = html(data(null, null))
        assertTrue(FILTER_NO_SCHOOLS_TEXT in out)
        assertFalse(FilterIds.ICSEA_MIN in out)
    }

    @Test
    fun `the summary counts matches and unknowns`() {
        val d = data(1000, 1100, null)
        assertTrue("Switch on a filter" in filterSummaryText(d.apply(FilterSelection())))
        assertTrue("1 of 3 suburbs have no ICSEA" in filterSummaryText(d.apply(FilterSelection())))
        assertEquals(
            "1 of 3 suburbs match. 1 of 3 suburbs have no ICSEA and never match.",
            filterSummaryText(d.apply(FilterSelection(IcseaRange(1050, 1200)))),
        )
    }

    @Test
    fun `text is escaped`() {
        val d = FilterData.from(SuburbFiltersResponse(listOf(SuburbFilterValue("a", 1000)), "<b>x</b>", null))
        assertFalse("<b>x</b>" in html(d))
    }
}
