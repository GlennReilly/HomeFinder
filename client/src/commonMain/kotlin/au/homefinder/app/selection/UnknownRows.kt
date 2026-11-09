package au.homefinder.app.selection

/**
 * Rows whose value is unknown are left off a card (ADR 0058) so it shows only what is known. While customising every row is listed, so an
 * unknown one can still be seen there; the card carries no note about the rows left off.
 */
object UnknownRows {
    fun isUnknown(row: CardRow): Boolean = row.value == null

    /** The rows to draw: all of them while customising; otherwise those you did not hide, without the unknown ones unless [showUnknown]. */
    fun visible(arranged: List<ArrangedRow>, customising: Boolean, showUnknown: Boolean): List<ArrangedRow> =
        arranged.filter { customising || (!it.hidden && (showUnknown || !isUnknown(it.row))) }

    /** How many unknown rows are left off (you did not hide them); none while customising. Tells "all hidden by you" from "all unknown". */
    fun unknownCount(arranged: List<ArrangedRow>, customising: Boolean): Int =
        if (customising) 0 else arranged.count { !it.hidden && isUnknown(it.row) }
}
