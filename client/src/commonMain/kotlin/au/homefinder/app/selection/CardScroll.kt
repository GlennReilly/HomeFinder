package au.homefinder.app.selection

/** What the card area is showing, for deciding whether its scroll position carries over (ADR 0053). */
enum class CardKind { NONE, SUBURB, LOT }

/** Where to put the scroll position after a redraw, and the memory to carry into the next one. */
data class ScrollPlan(val memory: CardScroll, val restoreTo: Int)

/**
 * Remembers how far the card was scrolled so that choosing another suburb keeps the same row in view, for comparing suburbs. The position
 * is read from the page only when the last thing drawn was a loaded card: the short Loading and Failed cards cannot scroll, and would
 * report 0. The position is dropped (back to the top) when the kind of card changes: a lot after a suburb, a card after none.
 */
data class CardScroll(val kind: CardKind = CardKind.NONE, val remembered: Int = 0, val lastWasLoaded: Boolean = false) {
    /**
     * For a redraw into [next] ([loaded] when it is a full card) with the page currently scrolled to [currentTop]:
     * the position to restore, and the memory for the redraw after it.
     */
    fun plan(next: CardKind, loaded: Boolean, currentTop: Int): ScrollPlan {
        val position = when {
            next == CardKind.NONE || next != kind -> 0
            lastWasLoaded -> currentTop.coerceAtLeast(0)
            else -> remembered
        }
        return ScrollPlan(CardScroll(next, position, lastWasLoaded = loaded && next != CardKind.NONE), position)
    }
}
