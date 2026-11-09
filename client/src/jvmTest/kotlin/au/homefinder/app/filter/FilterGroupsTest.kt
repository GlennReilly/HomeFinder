package au.homefinder.app.filter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterGroupsTest {
    @Test
    fun `default grouping lists every criterion once and only the first group starts open`() {
        val arranged = FilterGroups.arrange()
        assertEquals(listOf("People and area", "Crime", "Location", "Schools"), arranged.map { it.title })
        assertEquals(FilterCriterion.entries.toSet(), arranged.flatMap { it.criteria }.toSet())
        assertEquals(FilterCriterion.entries.size, arranged.sumOf { it.criteria.size })
        assertEquals(listOf(true, false, false, false), arranged.map { it.open })
    }

    @Test
    fun `a criterion no group lists goes to a final More group`() {
        val groups = listOf(FilterGroup("Only", listOf(FilterCriterion.ZONED), openByDefault = true))
        val arranged = FilterGroups.arrange(groups = groups)
        assertEquals(listOf("Only", FilterGroups.OTHER_TITLE), arranged.map { it.title })
        assertEquals(FilterCriterion.entries - FilterCriterion.ZONED, arranged.last().criteria)
    }

    @Test
    fun `a group with a switched-on criterion starts open`() {
        val arranged = FilterGroups.arrange(setOf(FilterCriterion.PROPERTY, FilterCriterion.ZONED))
        assertEquals(listOf("People and area", "Crime", "Schools"), arranged.filter { it.open }.map { it.title })
    }

    @Test
    fun `a group with no criteria is left out`() {
        val arranged = FilterGroups.arrange(groups = listOf(FilterGroup("Empty", emptyList())) + FilterGroups.DEFAULT)
        assertFalse(arranged.any { it.title == "Empty" })
    }

    @Test
    fun `the on text counts the group's switched-on criteria and is empty for none`() {
        val crime = FilterGroups.arrange().first { it.title == "Crime" }
        assertEquals("", FilterGroups.onText(crime, emptySet()))
        assertEquals("1 on", FilterGroups.onText(crime, setOf(FilterCriterion.VIOLENT, FilterCriterion.ZONED)))
        assertEquals("2 on", FilterGroups.onText(crime, setOf(FilterCriterion.VIOLENT, FilterCriterion.PROPERTY)))
    }

    @Test
    fun `element ids come from the title`() {
        val people = FilterGroups.arrange().first()
        assertEquals("filter-group-people-and-area", people.id)
        assertEquals("filter-group-people-and-area-count", people.countId)
        assertTrue(FilterGroups.idOf("More") == "filter-group-more")
    }

    @Test
    fun `the zoned NAPLAN filter sits first in the Schools group, before the zoned ICSEA filter (ADR 0078), and counts in its N on`() {
        val schools = FilterGroups.arrange().first { it.title == "Schools" }
        assertEquals(listOf(FilterCriterion.NAPLAN, FilterCriterion.ZONED), schools.criteria)
        assertEquals("1 on", FilterGroups.onText(schools, setOf(FilterCriterion.NAPLAN)))
        assertTrue(FilterGroups.arrange(setOf(FilterCriterion.NAPLAN)).first { it.title == "Schools" }.open)
    }
}
