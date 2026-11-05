package au.homefinder.app.selection

import kotlin.test.Test
import kotlin.test.assertEquals

class CardScrollTest {
    @Test
    fun `a card opened from nothing starts at the top`() {
        val plan = CardScroll().plan(CardKind.SUBURB, loaded = false, currentTop = 120)
        assertEquals(0, plan.restoreTo)
    }

    @Test
    fun `the position survives choosing another suburb, through the Loading card`() {
        var memory = CardScroll().plan(CardKind.SUBURB, loaded = true, currentTop = 0).memory
        // The user scrolls to 480, then taps another suburb: the Loading card is short, so the page reports 0 afterwards.
        val loading = memory.plan(CardKind.SUBURB, loaded = false, currentTop = 480)
        assertEquals(480, loading.restoreTo, "read from the loaded card before it was replaced")
        memory = loading.memory
        val loaded = memory.plan(CardKind.SUBURB, loaded = true, currentTop = 0)
        assertEquals(480, loaded.restoreTo, "the collapsed 0 of the Loading card is ignored")
        assertEquals(300, loaded.memory.plan(CardKind.SUBURB, loaded = true, currentTop = 300).restoreTo, "then it follows the user again")
    }

    @Test
    fun `a redraw of the same loaded card, such as Customise, keeps where it is`() {
        val memory = CardScroll(CardKind.SUBURB, remembered = 0, lastWasLoaded = true)
        assertEquals(250, memory.plan(CardKind.SUBURB, loaded = true, currentTop = 250).restoreTo)
    }

    @Test
    fun `a different kind of card or no card goes back to the top`() {
        val memory = CardScroll(CardKind.SUBURB, remembered = 300, lastWasLoaded = true)
        assertEquals(0, memory.plan(CardKind.LOT, loaded = false, currentTop = 300).restoreTo)
        assertEquals(0, memory.plan(CardKind.NONE, loaded = false, currentTop = 300).restoreTo)
        val closed = memory.plan(CardKind.NONE, loaded = false, currentTop = 300).memory
        assertEquals(0, closed.plan(CardKind.SUBURB, loaded = true, currentTop = 0).restoreTo, "reopened from nothing")
    }

    @Test
    fun `a failed card does not overwrite the remembered position`() {
        val memory = CardScroll(CardKind.SUBURB, remembered = 400, lastWasLoaded = true)
        val failed = memory.plan(CardKind.SUBURB, loaded = false, currentTop = 400)
        assertEquals(400, failed.memory.plan(CardKind.SUBURB, loaded = true, currentTop = 0).restoreTo)
    }
}
