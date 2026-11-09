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
    fun `rows you hid yourself stay hidden even when unknown ones are shown`() {
        assertEquals(listOf("A", "E"), UnknownRows.visible(CardLayout(hidden = setOf("C")).arrange(rows), customising = false, showUnknown = false).map { it.row.label })
    }
}
