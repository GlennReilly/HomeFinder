package au.homefinder.app.selection

import au.homefinder.shared.model.Confidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardLayoutTest {
    private fun row(label: String) = CardRow(label, "v", "src", null, null, Confidence.HIGH)
    private val rows = listOf("A", "B", "C", "D").map(::row)
    private fun labels(layout: CardLayout) = layout.visible(rows).map { it.label }

    @Test
    fun `the default layout shows every row in the default order`() {
        assertTrue(CardLayout().isDefault)
        assertEquals(listOf("A", "B", "C", "D"), labels(CardLayout()))
    }

    @Test
    fun `hiding a row keeps its place and showing it again brings it back`() {
        val hidden = CardLayout().toggle("B")
        assertEquals(listOf("A", "C", "D"), labels(hidden))
        assertTrue(hidden.arrange(rows).single { it.hidden }.row.label == "B")
        assertEquals(listOf("A", "B", "C", "D"), labels(hidden.toggle("B")))
    }

    @Test
    fun `rows move up and down by one, and stop at the ends`() {
        val moved = CardLayout().moveDown("A", rows)
        assertEquals(listOf("B", "A", "C", "D"), labels(moved))
        assertEquals(listOf("B", "C", "A", "D"), labels(moved.moveDown("A", rows)))
        assertEquals(listOf("B", "A", "C", "D"), labels(moved.moveDown("A", rows).moveUp("A", rows)))
        assertEquals(CardLayout(), CardLayout().moveUp("A", rows))
        assertEquals(CardLayout(), CardLayout().moveDown("D", rows))
        assertEquals(CardLayout(), CardLayout().moveUp("Nope", rows))
    }

    @Test
    fun `a hidden row moves with the others and stays hidden`() {
        val layout = CardLayout().toggle("B").moveDown("B", rows)
        assertEquals(listOf("A", "C", "D"), labels(layout))
        assertEquals(listOf("A", "C", "B", "D"), layout.arrange(rows).map { it.row.label })
    }

    @Test
    fun `new rows follow the known ones and rows that went away are ignored`() {
        val layout = CardLayout(order = listOf("C", "Gone", "A"), hidden = setOf("Gone"))
        assertEquals(listOf("C", "A", "B", "D"), labels(layout))
    }

    @Test
    fun `a layout survives being written and read back`() {
        val layout = CardLayout().moveDown("A", rows).toggle("C")
        assertEquals(layout, CardLayout.decode(layout.encode()))
        val hiddenOnly = CardLayout().toggle("B")
        assertEquals(hiddenOnly, CardLayout.decode(hiddenOnly.encode()))
        assertEquals(listOf("A", "C", "D"), labels(CardLayout.decode(hiddenOnly.encode())))
    }

    @Test
    fun `labels with commas survive and nothing stored gives the default`() {
        val layout = CardLayout(order = listOf("Price change, 12 months"), hidden = setOf("Price change, 12 months"))
        assertEquals(layout, CardLayout.decode(layout.encode()))
        assertTrue(CardLayout.decode(null).isDefault)
        assertTrue(CardLayout.decode("").isDefault)
        assertFalse(CardLayout.decode("garbage\n\n?x").let { it.order.isNotEmpty() || it.hidden.isNotEmpty() })
    }

    @Test
    fun `a layout saved with the removed Sales behind the prices row still works`() {
        val saved = CardLayout(order = listOf("C", "Sales behind the prices", "A"), hidden = setOf("Sales behind the prices", "B"))
        assertEquals(listOf("C", "A", "D"), labels(saved), "the removed label is ignored, the rest keeps its order and hiding")
        assertEquals(listOf("C", "A", "B", "D"), saved.arrange(rows).map { it.row.label })
        assertEquals(listOf("B"), saved.arrange(rows).filter { it.hidden }.map { it.row.label })
    }

    @Test
    fun `a layout saved with the removed Zoned schools row still works on the real Suburb Card (ADR 0073)`() {
        val real = SuburbCardRows.of(
            au.homefinder.shared.model.SuburbReport(
                "9001", "HAMILTON", "2303", au.homefinder.shared.model.Attribute.unknown("a"), au.homefinder.shared.model.Attribute.unknown("a"),
                au.homefinder.shared.model.Attribute.unknown("a"), au.homefinder.shared.model.Attribute.unknown("a"), au.homefinder.shared.model.Attribute.unknown("a"),
                au.homefinder.shared.model.Attribute.unknown("a"), au.homefinder.shared.model.Attribute.unknown("a"), au.homefinder.shared.model.Attribute.unknown("a"),
                au.homefinder.shared.model.Attribute.unknown("a"), au.homefinder.shared.model.Attribute.unknown("a"), au.homefinder.shared.model.Attribute.unknown("a"),
                au.homefinder.shared.model.Attribute.unknown("a"), au.homefinder.shared.model.Attribute.unknown("a"),
            ),
        )
        val saved = CardLayout(order = listOf("Parks", "Zoned schools", "Schools"), hidden = setOf("Zoned schools"))
        val groups = saved.arrangeGroups(real, CardGroups.SUBURB)
        val labels = groups.flatMap { g -> g.rows.map { it.row.label } }
        assertFalse("Zoned schools" in labels)
        assertEquals(1, labels.count { it == "Schools" })
        assertTrue(groups.flatMap { it.rows }.none { it.hidden }, "the old hidden label does not hide the Schools row")
        assertTrue(groups.none { it.title == CardGroups.OTHER_TITLE }, "no stray More group")
    }
}
