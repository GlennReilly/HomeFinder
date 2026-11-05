package au.homefinder.app.ui

import au.homefinder.app.filter.FilterCriterion
import au.homefinder.app.filter.FilterData
import au.homefinder.app.filter.FilterSelection
import au.homefinder.shared.model.FilterProvenance
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
    fun `the panel has a switch and one slider spanning the data, in steps of ten, starting at the lowest value for at least`() {
        val out = html(data(1032, 961, null))
        assertTrue("""id="${FilterIds.on(FilterCriterion.ICSEA)}"""" in out)
        assertTrue("""id="${FilterIds.limit(FilterCriterion.ICSEA)}"""" in out)
        assertFalse("filter-icsea-min" in out || "filter-icsea-max" in out, "no second slider")
        assertTrue("""min="960"""" in out && """max="1040"""" in out && """step="10"""" in out && """value="960"""" in out, out)
        assertTrue("""<span id="filter-icsea-limit-prefix">At least </span><span id="filter-icsea-limit-value">960</span>""" in out, out)
    }

    @Test
    fun `it says ICSEA is not school quality and gives the source and date`() {
        val out = html(data(1032))
        assertTrue("not a measure of school quality" in out)
        assertTrue("Source: NSW schools · as of 2026-10-02" in out, out)
    }

    @Test
    fun `without any ICSEA it says the schools are not loaded and has no ICSEA sliders`() {
        val out = html(data(null, null))
        assertTrue(FILTER_NO_DATA_TEXT in out)
        assertFalse(FilterIds.limit(FilterCriterion.ICSEA) in out)
    }

    @Test
    fun `the summary counts the suburbs that match every switched-on filter`() {
        val d = data(1000, 1100, null)
        assertEquals("Switch on a filter to highlight matching suburbs.", filterSummaryText(d.apply(FilterSelection())))
        assertEquals(
            "1 of 3 suburbs match.",
            filterSummaryText(d.apply(FilterSelection(mapOf(FilterCriterion.ICSEA to 1050.0)))),
        )
        val two = FilterSelection(mapOf(FilterCriterion.ICSEA to 0.0, FilterCriterion.RENTED to 100.0))
        assertEquals("0 of 3 suburbs match all 2 filters.$NO_MATCH_HINT", filterSummaryText(d.apply(two)))
    }

    @Test
    fun `each slider says how many suburbs have no value for it`() {
        val d = data(1000, 1100, null)
        assertEquals("1 of 3 suburbs have no ICSEA and never match.", filterUnknownText(d, FilterCriterion.ICSEA))
        assertTrue("""id="${FilterIds.unknown(FilterCriterion.ICSEA)}"""" in html(d))
    }

    private val full = FilterData.from(
        SuburbFiltersResponse(
            listOf(SuburbFilterValue("a", 1000, 1.5, 0.2, 0.45, 8, 4.2, 12.5, zonedIcsea = 1040), SuburbFilterValue("b", 1100, 12.0, null, 0.2, 3, 30.0, 40.0, zonedIcsea = 1010)),
            "NSW schools", LocalDate(2026, 10, 2),
            beach = FilterProvenance("OpenStreetMap", LocalDate(2026, 10, 29)), parks = FilterProvenance("OpenStreetMap", LocalDate(2026, 10, 29)),
            rented = FilterProvenance("ABS Census", LocalDate(2021, 8, 10)), seifa = FilterProvenance("ABS SEIFA", LocalDate(2021, 8, 10)), crime = FilterProvenance("BOCSAR", LocalDate(2026, 6, 30)), zoned = FilterProvenance("NSW DoE catchments", LocalDate(2026, 11, 5)),
        ),
    )

    @Test
    fun `every criterion with data gets a switch, two sliders in its own unit and its source`() {
        val out = html(full)
        FilterCriterion.entries.forEach { c ->
            assertTrue("""id="${FilterIds.on(c)}"""" in out && """id="${FilterIds.limit(c)}"""" in out, c.name)
        }
        assertTrue("""<span id="filter-rented-limit-prefix">At most </span><span id="filter-rented-limit-value">45%</span>""" in out, out)
        assertTrue("""<span id="filter-beach-limit-prefix">At most </span><span id="filter-beach-limit-value">12 km</span>""" in out)
        assertTrue("""<span id="filter-seifa-limit-prefix">At least </span><span id="filter-seifa-limit-value">3</span>""" in out)
        assertTrue("Homes rented: Source: ABS Census · as of 2021-08-10" in out)
        assertTrue("Violent crime: Source: BOCSAR · as of 2026-06-30" in out)
        assertTrue("Break-ins, car and home theft: Source: BOCSAR · as of 2026-06-30" in out)
        assertTrue("Distance to the beach: Source: OpenStreetMap · as of 2026-10-29" in out)
        assertTrue("a suburb with no park mapped has no value" in out)
        assertTrue("1 of 2 suburbs have no mapped park and never match." in out)
    }

    @Test
    fun `a criterion whose data is not loaded says so instead of showing sliders`() {
        val out = html(data(1000, 1100))
        assertTrue(FilterCriterion.RENTED.notLoadedText in out && FilterCriterion.BEACH.notLoadedText in out)
        assertFalse(FilterIds.limit(FilterCriterion.RENTED) in out)
        assertTrue(FilterIds.limit(FilterCriterion.ICSEA) in out)
    }

    @Test
    fun `with nothing loaded at all the panel says there is nothing to filter on`() {
        assertTrue(FILTER_NO_DATA_TEXT in html(data(null, null)))
    }

    @Test
    fun `text is escaped`() {
        val d = FilterData.from(SuburbFiltersResponse(listOf(SuburbFilterValue("a", 1000)), "<b>x</b>", null))
        assertFalse("<b>x</b>" in html(d))
    }

    @Test
    fun `a Reset all button is drawn, off while no filter is on and on once one is`() {
        val d = data(1000, 1100)
        val idle = html(d)
        assertTrue("""id="${FilterIds.RESET}"""" in idle && FILTER_RESET_TEXT in idle)
        assertTrue(Regex("""<button[^>]*id="${FilterIds.RESET}"[^>]*disabled""").containsMatchIn(idle) || Regex("""<button[^>]*disabled[^>]*id="${FilterIds.RESET}"""").containsMatchIn(idle), idle)
        val active = createHTML().div { filterPanel(d, d.apply(FilterSelection(mapOf(FilterCriterion.ICSEA to 1000.0)))) }
        assertFalse(Regex("""<button[^>]*id="${FilterIds.RESET}"[^>]*disabled""").containsMatchIn(active) || Regex("""<button[^>]*disabled[^>]*id="${FilterIds.RESET}"""").containsMatchIn(active), active)
    }

    @Test
    fun `a capped crime slider starts on No limit, with no words before it, and spans the lowest 90 percent plus that position`() {
        val values = (1..19).map { SuburbFilterValue("s$it", violentPer1000 = it.toDouble()) } + SuburbFilterValue("outlier", violentPer1000 = 100.0)
        val d = FilterData.from(SuburbFiltersResponse(values, "S", LocalDate(2026, 10, 2), crime = FilterProvenance("BOCSAR", LocalDate(2026, 6, 30))))
        val out = html(d)
        assertTrue("""<span id="filter-violent-limit-prefix"></span><span id="filter-violent-limit-value">No limit</span>""" in out, out)
        assertTrue(Regex("""id="filter-violent-limit"[^>]*max="19"[^>]*value="19"""").containsMatchIn(out) || Regex("""id="filter-violent-limit"[^>]*value="19"[^>]*max="19"""").containsMatchIn(out), out)
    }
}
