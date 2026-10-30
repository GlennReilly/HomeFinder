package au.homefinder.app.filter

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
        val result = sample.apply(FilterSelection(IcseaRange(961, 1032)))
        assertEquals(setOf("a", "b"), result.passingIds)
        assertEquals(2, result.matching)
    }

    @Test
    fun `a suburb with no ICSEA never passes and is counted as unknown`() {
        val result = sample.apply(FilterSelection(IcseaRange(0, 2000)))
        assertEquals(setOf("a", "b", "d"), result.passingIds)
        assertEquals(1, result.unknownIcsea)
        assertEquals(4, result.total)
    }

    @Test
    fun `the unknown count is shown even before a filter is on`() {
        assertEquals(1, sample.apply(FilterSelection()).unknownIcsea)
    }

    @Test
    fun `the slider bounds are the known range widened to whole steps`() {
        assertEquals(IcseaRange(960, 1110), sample.icseaBounds)
        assertEquals(IcseaRange(1000, 1000), data("x" to 1000).icseaBounds)
    }

    @Test
    fun `with no ICSEA anywhere there are no bounds`() {
        val none = data("a" to null, "b" to null)
        assertNull(none.icseaBounds)
        assertFalse(none.hasIcsea)
        assertEquals(emptySet(), none.apply(FilterSelection(IcseaRange(0, 2000))).passingIds)
    }

    @Test
    fun `no suburbs at all is fine`() {
        val empty = data()
        assertNull(empty.icseaBounds)
        assertEquals(0, empty.apply(FilterSelection()).total)
    }

    @Test
    fun `provenance is kept from the response`() {
        assertEquals("Schools", sample.source)
        assertEquals(day, sample.asOf)
    }

    @Test
    fun `a range cannot have its minimum above its maximum`() {
        assertFailsWith<IllegalArgumentException> { IcseaRange(1100, 1000) }
        assertTrue(1000 in IcseaRange(1000, 1000))
    }
}
