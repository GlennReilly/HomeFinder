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

    private fun icsea(limit: Double) = FilterSelection(mapOf(FilterCriterion.ICSEA to limit))

    private val sample = data("a" to 1032, "b" to 961, "c" to null, "d" to 1105)

    @Test
    fun `nothing is switched on at first so every suburb stays as it is`() {
        val result = sample.apply(FilterSelection())
        assertNull(result.passingIds)
        assertEquals(4, result.matching)
        assertFalse(FilterSelection().isActive)
    }

    @Test
    fun `an ICSEA limit passes the suburbs at or above it, the limit included`() {
        val result = sample.apply(icsea(1032.0))
        assertEquals(setOf("a", "d"), result.passingIds)
        assertEquals(2, result.matching)
    }

    @Test
    fun `a suburb with no ICSEA never passes and is counted as unknown`() {
        val result = sample.apply(icsea(0.0))
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
        assertEquals(emptySet(), none.apply(icsea(0.0)).passingIds)
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

    @Test
    fun `each criterion has one fixed direction, and its slider starts where every suburb passes`() {
        assertEquals(
            listOf(Direction.AT_LEAST, Direction.AT_MOST, Direction.AT_LEAST, Direction.AT_MOST, Direction.AT_LEAST, Direction.AT_MOST, Direction.AT_MOST),
            listOf(FilterCriterion.ICSEA, FilterCriterion.BEACH, FilterCriterion.PARKS, FilterCriterion.RENTED, FilterCriterion.SEIFA, FilterCriterion.VIOLENT, FilterCriterion.PROPERTY)
                .map { it.direction },
        )
        val bounds = FilterRange(960.0, 1110.0)
        assertEquals(960.0, FilterCriterion.ICSEA.startLimit(bounds), "at least: the lowest value")
        assertEquals(1110.0, FilterCriterion.BEACH.startLimit(bounds), "at most: the highest value")
        assertTrue(Direction.AT_LEAST.passes(5.0, 5.0) && Direction.AT_MOST.passes(5.0, 5.0), "the limit itself passes")
        assertFalse(Direction.AT_LEAST.passes(4.9, 5.0) || Direction.AT_MOST.passes(5.1, 5.0))
        assertEquals("At least", Direction.AT_LEAST.word)
        assertEquals("40 per 1,000", FilterCriterion.VIOLENT.format(40.0))
    }

    @Test
    fun `with every slider at its start limit, switching a criterion on loses no suburb that has a value`() {
        FilterCriterion.entries.filter(all::hasData).forEach { criterion ->
            val start = criterion.startLimit(checkNotNull(all.bounds(criterion)))
            val result = all.apply(FilterSelection(mapOf(criterion to start)))
            assertEquals(all.total - all.unknown(criterion), result.matching, criterion.name)
        }
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
                    FilterCriterion.BEACH to 5.0,
                    FilterCriterion.RENTED to 50.0,
                ),
            ),
        )
        assertEquals(setOf("near", "nopark"), result.passingIds, "'far' is too far and 'nocensus' has no rented share")
        assertEquals(1, result.unknown.getValue(FilterCriterion.RENTED))
        assertEquals(1, result.unknown.getValue(FilterCriterion.PARKS))
        assertEquals(0, result.unknown.getValue(FilterCriterion.BEACH))
        // A suburb with no value for a switched-on criterion never passes, however well it does on the others.
        assertEquals(setOf("near", "far", "nocensus"), all.apply(FilterSelection(mapOf(FilterCriterion.PARKS to 0.0))).passingIds)
    }

    @Test
    fun `element ids come from the criterion key and are found again from any of them`() {
        FilterCriterion.entries.forEach { criterion ->
            assertEquals(criterion, au.homefinder.app.ui.FilterIds.criterionOf(au.homefinder.app.ui.FilterIds.limit(criterion)))
            assertEquals(criterion, au.homefinder.app.ui.FilterIds.criterionOf(au.homefinder.app.ui.FilterIds.limitOut(criterion)))
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
        val result = d.apply(FilterSelection(mapOf(FilterCriterion.VIOLENT to 10.0, FilterCriterion.PROPERTY to 50.0)))
        assertEquals(setOf("calm"), result.passingIds, "'busy' is over both limits and 'tiny' has no rate")
        assertEquals(1, result.unknown.getValue(FilterCriterion.VIOLENT))
        assertEquals("BOCSAR", d.source(FilterCriterion.PROPERTY)?.source)
    }

    /** Nineteen suburbs from 1 to 19 per 1,000, one outlier at 100, and one with no rate. */
    private val withOutlier = FilterData.from(
        SuburbFiltersResponse(
            (1..19).map { SuburbFilterValue("s$it", violentPer1000 = it.toDouble()) } + SuburbFilterValue("outlier", violentPer1000 = 100.0) + SuburbFilterValue("none"),
            "Schools", day, crime = FilterProvenance("BOCSAR", day),
        ),
    )

    @Test
    fun `a capped slider spans the lowest 90 percent of suburbs plus one No limit position`() {
        assertEquals(18.0, withOutlier.cap(FilterCriterion.VIOLENT), "the 18th of 20 values, rounded up to a step")
        assertEquals(19.0, withOutlier.noLimitPosition(FilterCriterion.VIOLENT))
        assertEquals(FilterRange(1.0, 19.0), withOutlier.bounds(FilterCriterion.VIOLENT))
        assertEquals(19.0, FilterCriterion.VIOLENT.startLimit(withOutlier.bounds(FilterCriterion.VIOLENT)!!), "it starts on No limit")
    }

    @Test
    fun `the last position says No limit and applies none, though a suburb with no rate still never matches`() {
        assertEquals("No limit", withOutlier.limitText(FilterCriterion.VIOLENT, 19.0))
        assertEquals("", withOutlier.limitPrefix(FilterCriterion.VIOLENT, 19.0))
        assertEquals("18 per 1,000", withOutlier.limitText(FilterCriterion.VIOLENT, 18.0))
        assertEquals("At most ", withOutlier.limitPrefix(FilterCriterion.VIOLENT, 18.0))
        val none = withOutlier.apply(FilterSelection(mapOf(FilterCriterion.VIOLENT to 19.0))).passingIds!!
        assertEquals(20, none.size, "the outlier passes with no limit")
        assertTrue("none" !in none)
    }

    @Test
    fun `at the cap itself the limit is an ordinary one and the outlier is out`() {
        val atCap = withOutlier.apply(FilterSelection(mapOf(FilterCriterion.VIOLENT to 18.0))).passingIds!!
        assertEquals((1..18).map { "s$it" }.toSet(), atCap, "18 passes, 19 and the outlier do not")
    }

    @Test
    fun `without suburbs above the cap there is no extra position, and other criteria are never capped`() {
        // Three values: the 90th percentile is the highest, so nothing lies above the cap.
        assertNull(all.cap(FilterCriterion.RENTED))
        val small = FilterData.from(
            SuburbFiltersResponse(listOf(SuburbFilterValue("a", violentPer1000 = 4.2), SuburbFilterValue("b", violentPer1000 = 31.0)), "S", day, crime = FilterProvenance("B", day)),
        )
        assertNull(small.cap(FilterCriterion.VIOLENT))
        assertNull(small.noLimitPosition(FilterCriterion.VIOLENT))
        assertEquals(FilterRange(4.0, 31.0), small.bounds(FilterCriterion.VIOLENT))
        assertEquals("31 per 1,000", small.limitText(FilterCriterion.VIOLENT, 31.0))
        assertNull(withOutlier.cap(FilterCriterion.ICSEA), "no values")
    }
}
