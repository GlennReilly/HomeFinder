package au.homefinder.app.selection

import au.homefinder.shared.model.Confidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardGroupsTest {
    private fun row(label: String) = CardRow(label, "v", "src", null, null, Confidence.HIGH)
    private val groups = listOf(CardGroup("First", listOf("A", "B", "C"), openByDefault = true), CardGroup("Second", listOf("D", "E")))
    private val rows = listOf("A", "B", "C", "D", "E").map(::row)
    private fun titles(layout: CardLayout, list: List<CardRow> = rows) = layout.arrangeGroups(list, groups).map { it.title }
    private fun labels(layout: CardLayout, title: String) = layout.arrangeGroups(rows, groups).first { it.title == title }.rows.map { it.row.label }

    @Test
    fun `rows fall into their groups in the default order`() {
        val arranged = CardLayout().arrangeGroups(rows, groups)
        assertEquals(listOf("First", "Second"), arranged.map { it.title })
        assertEquals(listOf("A", "B", "C"), arranged[0].rows.map { it.row.label })
        assertTrue(arranged[0].openByDefault)
        assertFalse(arranged[1].openByDefault)
    }

    @Test
    fun `a row no group lists lands in a final More group, and an empty group is left out`() {
        val arranged = CardLayout().arrangeGroups(rows.filter { it.label != "D" && it.label != "E" } + row("Z"), groups)
        assertEquals(listOf("First", CardGroups.OTHER_TITLE), arranged.map { it.title })
        assertEquals(listOf("Z"), arranged.last().rows.map { it.row.label })
    }

    @Test
    fun `a row moves within its group and stops at the group's ends`() {
        val moved = CardLayout().moveRowInGroup("A", rows, groups, 1)
        assertEquals(listOf("B", "A", "C"), labels(moved, "First"))
        assertEquals(listOf("D", "E"), labels(moved, "Second"))
        assertEquals(CardLayout(), CardLayout().moveRowInGroup("A", rows, groups, -1), "the top of a group stays put")
        assertEquals(CardLayout(), CardLayout().moveRowInGroup("C", rows, groups, 1), "a row never crosses into the next group")
        assertEquals(CardLayout(), CardLayout().moveRowInGroup("Nope", rows, groups, 1))
    }

    @Test
    fun `groups move up and down and stop at the ends`() {
        val moved = CardLayout().moveGroup("First", rows, groups, 1)
        assertEquals(listOf("Second", "First"), titles(moved))
        assertEquals(listOf("A", "B", "C"), labels(moved, "First"), "rows keep their order inside a moved group")
        assertEquals(CardLayout(), CardLayout().moveGroup("First", rows, groups, -1))
        assertEquals(CardLayout(), moved.moveGroup("Second", rows, groups, -1).copy(groupOrder = emptyList()))
        assertFalse(moved.isDefault)
    }

    @Test
    fun `the group order survives being written and read back, next to rows and hidden rows`() {
        val layout = CardLayout().moveGroup("First", rows, groups, 1).moveRowInGroup("D", rows, groups, 1).toggle("B")
        assertEquals(layout, CardLayout.decode(layout.encode()))
        assertEquals(listOf("Second", "First"), titles(CardLayout.decode(layout.encode())))
    }

    @Test
    fun `a layout saved before groups existed still reads, with groups in the default order`() {
        val old = CardLayout.decode("+B\n+A\n!C")
        assertEquals(listOf("First", "Second"), titles(old))
        assertEquals(listOf("B", "A", "C"), labels(old, "First"))
        assertTrue(old.arrangeGroups(rows, groups)[0].rows.single { it.row.label == "C" }.hidden)
    }

    @Test
    fun `every row of both real cards belongs to a group`() {
        val suburbLabels = CardGroups.SUBURB.flatMap { it.labels }
        assertEquals(suburbLabels.size, suburbLabels.toSet().size, "no suburb row is listed twice")
        val lotLabels = CardGroups.LOT.flatMap { it.labels }
        assertEquals(lotLabels.size, lotLabels.toSet().size, "no lot row is listed twice")
        assertTrue(CardGroups.SUBURB.first().openByDefault && CardGroups.SUBURB.drop(1).none { it.openByDefault })
    }
}
