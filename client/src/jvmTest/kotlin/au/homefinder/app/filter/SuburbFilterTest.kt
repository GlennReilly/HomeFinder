package au.homefinder.app.filter

import au.homefinder.shared.model.FilterProvenance
import au.homefinder.shared.model.SuburbFilterValue
import au.homefinder.shared.model.SuburbFiltersResponse
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuburbFilterTest {
    private val day = LocalDate(2026, 10, 2)
    private fun data(vararg pairs: Pair<String, Int?>) =
        FilterData.from(SuburbFiltersResponse(pairs.map { SuburbFilterValue(it.first, it.second) }, "Schools", day))

    private fun icsea(min: Double, max: Double) = FilterSelection(mapOf(FilterCriterion.ICSEA to FilterRange(min, max)))

    private val sample = data("a" to 1032, "b" to 961, "c" to null, "d" to 1105)

    @Test
    fun `nothing is switched on at first so every suburb stays as it is`() {
        val result = sample.apply(FilterSelection())
        assertNull(result.passingIds)
        assertEquals(4, result.matching)
        assertFalse(FilterSelection().isActive)
    }

    @Test
    fun `an ICSEA range passes the suburbs inside it, ends included`() {
        val result = sample.apply(icsea(961.0, 1032.0))
        assertEquals(setOf("a", "b"), result.passingIds)
        assertEquals(2, result.matching)
    }

    @Test
    fun `a suburb with no ICSEA never passes and is counted as unknown`() {
        val result = sample.apply(icsea(0.0, 2000.0))
        assertEquals(setOf("a", "b", "d"), result.passingIds)
        assertEquals(1, result.unknown.getValue(FilterCriterion.ICSEA))
        assertEquals(4, result.total)
    }

    @Test
    fun `the unknown counts are shown even before a filter is on`() {
        val result = sample.apply(FilterSelection())
        assertEquals(1, result.unknown.getValue(FilterCriterion.ICSEA))
        assertEquals(4, result.unknown.getValue(FilterCriterion.BEACH), "no suburb has a beach distance in this response")
    }

    @Test
    fun `the slider bounds are the known range widened to whole steps`() {
        assertEquals(FilterRange(960.0, 1110.0), sample.bounds(FilterCriterion.ICSEA))
        assertEquals(FilterRange(1000.0, 1000.0), data("x" to 1000).bounds(FilterCriterion.ICSEA))
    }

    @Test
    fun `with no ICSEA anywhere there are no bounds`() {
        val none = data("a" to null, "b" to null)
        assertNull(none.bounds(FilterCriterion.ICSEA))
        assertFalse(none.hasData(FilterCriterion.ICSEA))
        assertFalse(none.hasAnyData)
        assertEquals(emptySet(), none.apply(icsea(0.0, 2000.0)).passingIds)
    }

    @Test
    fun `no suburbs at all is fine`() {
        val empty = data()
        assertNull(empty.bounds(FilterCriterion.ICSEA))
        assertEquals(0, empty.apply(FilterSelection()).total)
    }

    @Test
    fun `provenance is kept from the response and a criterion with none has no source`() {
        assertEquals(FilterSource("Schools", day), sample.source(FilterCriterion.ICSEA))
        assertNull(sample.source(FilterCriterion.BEACH))
        assertNull(sample.source(FilterCriterion.RENTED))
    }

    @Test
    fun `a range cannot have its minimum above its maximum`() {
        assertFailsWith<IllegalArgumentException> { FilterRange(1100.0, 1000.0) }
        assertTrue(1000.0 in FilterRange(1000.0, 1000.0))
    }

    private val all = FilterData.from(
        SuburbFiltersResponse(
            listOf(
                SuburbFilterValue("near", averageIcsea = 1100, beachKm = 1.5, parkShare = 0.20, rentedShare = 0.45),
                SuburbFilterValue("far", averageIcsea = 1000, beachKm = 12.0, parkShare = 0.05, rentedShare = 0.20),
                SuburbFilterValue("nopark", averageIcsea = null, beachKm = 3.0, parkShare = null, rentedShare = 0.30),
                SuburbFilterValue("nocensus", averageIcsea = 1050, beachKm = 2.0, parkShare = 0.10, rentedShare = null),
            ),
            "Schools", day,
            beach = FilterProvenance("OSM", day), parks = FilterProvenance("OSM", day), rented = FilterProvenance("ABS", LocalDate(2021, 8, 10)),
        ),
    )

    @Test
    fun `shares are shown as percentages and the bounds are rounded out to whole units`() {
        assertEquals(FilterRange(5.0, 20.0), all.bounds(FilterCriterion.PARKS))
        assertEquals(FilterRange(20.0, 45.0), all.bounds(FilterCriterion.RENTED))
        assertEquals(FilterRange(1.0, 12.0), all.bounds(FilterCriterion.BEACH), "1.5 km rounds down to 1 and 12.0 stays")
        assertEquals("45%", FilterCriterion.RENTED.format(45.0))
        assertEquals("12 km", FilterCriterion.BEACH.format(12.0))
        assertEquals("1050", FilterCriterion.ICSEA.format(1050.0))
    }

    @Test
    fun `a suburb must pass every switched-on criterion, and the unknown counts are per criterion`() {
        val result = all.apply(
            FilterSelection(
                mapOf(
                    FilterCriterion.BEACH to FilterRange(0.0, 5.0),
                    FilterCriterion.RENTED to FilterRange(25.0, 100.0),
                ),
            ),
        )
        assertEquals(setOf("near", "nopark"), result.passingIds, "'far' is too far and 'nocensus' has no rented share")
        assertEquals(1, result.unknown.getValue(FilterCriterion.RENTED))
        assertEquals(1, result.unknown.getValue(FilterCriterion.PARKS))
        assertEquals(0, result.unknown.getValue(FilterCriterion.BEACH))
        // A suburb with no value for a switched-on criterion never passes, however well it does on the others.
        assertEquals(setOf("near", "far", "nocensus"), all.apply(FilterSelection(mapOf(FilterCriterion.PARKS to FilterRange(0.0, 100.0)))).passingIds)
    }

    @Test
    fun `element ids come from the criterion key and are found again from any of them`() {
        FilterCriterion.entries.forEach { criterion ->
            assertEquals(criterion, au.homefinder.app.ui.FilterIds.criterionOf(au.homefinder.app.ui.FilterIds.min(criterion)))
            assertEquals(criterion, au.homefinder.app.ui.FilterIds.criterionOf(au.homefinder.app.ui.FilterIds.maxOut(criterion)))
        }
        assertEquals("filter-icsea-on", au.homefinder.app.ui.FilterIds.on(FilterCriterion.ICSEA), "the ICSEA ids are unchanged")
        assertNull(au.homefinder.app.ui.FilterIds.criterionOf("filter-summary"))
    }

    @Test
    fun `crime rates are filterable per 1,000 residents, unknown where there is no rate`() {
        val d = FilterData.from(
            SuburbFiltersResponse(
                listOf(
                    SuburbFilterValue("calm", violentPer1000 = 4.2, propertyPer1000 = 20.0),
                    SuburbFilterValue("busy", violentPer1000 = 31.0, propertyPer1000 = 90.0),
                    SuburbFilterValue("tiny"),
                ),
                "Schools", day, crime = FilterProvenance("BOCSAR", day),
            ),
        )
        assertEquals(FilterRange(4.0, 31.0), d.bounds(FilterCriterion.VIOLENT))
        val result = d.apply(FilterSelection(mapOf(FilterCriterion.VIOLENT to FilterRange(0.0, 10.0), FilterCriterion.PROPERTY to FilterRange(0.0, 50.0))))
        assertEquals(setOf("calm"), result.passingIds, "'busy' is over both limits and 'tiny' has no rate")
        assertEquals(1, result.unknown.getValue(FilterCriterion.VIOLENT))
        assertEquals("BOCSAR", d.source(FilterCriterion.PROPERTY)?.source)
    }
}
