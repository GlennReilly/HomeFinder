package au.homefinder.app.selection

/**
 * Rows whose value is unknown are left off a card (ADR 0058) so it shows only what is known; a line under the rows says how many are left
 * out and offers to show them (with their source and reason). Rows you hid yourself (ADR 0039) are not counted, and while customising every
 * row is listed, as before.
 */
object UnknownRows {
    fun isUnknown(row: CardRow): Boolean = row.value == null

    /** The rows to draw: all of them while customising; otherwise those you did not hide, without the unknown ones unless [showUnknown]. */
    fun visible(arranged: List<ArrangedRow>, customising: Boolean, showUnknown: Boolean): List<ArrangedRow> =
        arranged.filter { customising || (!it.hidden && (showUnknown || !isUnknown(it.row))) }

    /** How many unknown rows are (or, when [showUnknown], would otherwise be) left off; none while customising. */
    fun unknownCount(arranged: List<ArrangedRow>, customising: Boolean): Int =
        if (customising) 0 else arranged.count { !it.hidden && isUnknown(it.row) }

    /**
     * The index in [visible] at which the rows "up to and including" [label] end, where [label] is judged by its place in the full
     * arrangement even if the row itself is not drawn (a hidden or unknown price change still marks where the Homely block goes).
     */
    fun splitAfter(arranged: List<ArrangedRow>, label: String, customising: Boolean, showUnknown: Boolean): Int {
        val at = arranged.indexOfFirst { it.row.label == label }
        if (at < 0) return 0
        return visible(arranged.take(at + 1), customising, showUnknown).size
    }

    fun note(count: Int, showing: Boolean): String {
        val rows = if (count == 1) "1 row without data" else "$count rows without data"
        return if (showing) "Showing $rows." else (if (count == 1) "$rows is hidden." else "$rows are hidden.")
    }

    fun toggleText(count: Int, showing: Boolean): String = when {
        showing -> if (count == 1) "Hide it again" else "Hide them again"
        else -> if (count == 1) "Show it" else "Show them"
    }
}
