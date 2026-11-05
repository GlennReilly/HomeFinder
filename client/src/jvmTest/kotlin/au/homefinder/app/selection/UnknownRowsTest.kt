package au.homefinder.app.selection

import au.homefinder.shared.model.Confidence
import kotlin.test.Test
import kotlin.test.assertEquals

class UnknownRowsTest {
    private fun row(label: String, value: String?) = CardRow(label, value, "S", null, null, Confidence.NONE)
    private val rows = listOf(row("A", "1"), row("B", null), row("C", "3"), row("D", null), row("E", "5"))
    private val arranged = CardLayout().arrange(rows)

    @Test
    fun `unknown rows are left off unless shown, and customising lists everything`() {
        assertEquals(listOf("A", "C", "E"), UnknownRows.visible(arranged, customising = false, showUnknown = false).map { it.row.label })
        assertEquals(listOf("A", "B", "C", "D", "E"), UnknownRows.visible(arranged, customising = false, showUnknown = true).map { it.row.label })
        assertEquals(listOf("A", "B", "C", "D", "E"), UnknownRows.visible(arranged, customising = true, showUnknown = false).map { it.row.label })
    }

    @Test
    fun `rows you hid yourself are not counted as unknown, and nothing is counted while customising`() {
        assertEquals(2, UnknownRows.unknownCount(arranged, customising = false))
        assertEquals(1, UnknownRows.unknownCount(CardLayout(hidden = setOf("B")).arrange(rows), customising = false))
        assertEquals(0, UnknownRows.unknownCount(arranged, customising = true))
        assertEquals(listOf("A", "E"), UnknownRows.visible(CardLayout(hidden = setOf("C")).arrange(rows), customising = false, showUnknown = false).map { it.row.label })
    }

    @Test
    fun `the note and the button read in the singular and the plural`() {
        assertEquals("1 row without data is hidden.", UnknownRows.note(1, showing = false))
        assertEquals("3 rows without data are hidden.", UnknownRows.note(3, showing = false))
        assertEquals("Showing 3 rows without data.", UnknownRows.note(3, showing = true))
        assertEquals("Show it", UnknownRows.toggleText(1, showing = false))
        assertEquals("Show them", UnknownRows.toggleText(2, showing = false))
        assertEquals("Hide it again", UnknownRows.toggleText(1, showing = true))
        assertEquals("Hide them again", UnknownRows.toggleText(2, showing = true))
    }
}
