package au.homefinder.app.selection

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.SuburbReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchoolsJumpTest {
    private fun <T> unknown() = Attribute.unknown<T>("s", note = "n")
    private fun report(id: String) = SuburbReport(
        id, "S$id", null, Attribute(1.0, Confidence.HIGH, "s"),
        unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(),
    )
    private fun loaded(id: String) = MapUiState(id, SuburbCardState.Loaded(report(id)))
    private fun loading(id: String) = MapUiState(id, SuburbCardState.Loading(id, null))

    @Test
    fun `asking for the suburb whose card is loaded goes at once and leaves nothing pending`() {
        val decision = SchoolsJump().requested("9001", loaded("9001"))
        assertTrue(decision.go)
        assertNull(decision.next.pending)
    }

    @Test
    fun `asking for another suburb waits through its loading card, then goes once`() {
        var decision = SchoolsJump().requested("9002", loaded("9001"))
        assertFalse(decision.go, "the suburb on show is the one being replaced")
        assertEquals("9002", decision.next.pending)
        decision = decision.next.onState(loading("9002"))
        assertFalse(decision.go)
        assertEquals("9002", decision.next.pending)
        decision = decision.next.onState(loaded("9002"))
        assertTrue(decision.go)
        assertFalse(decision.next.onState(loaded("9002")).go, "carried out once only")
    }

    @Test
    fun `the wish is dropped when something else is selected, the card fails or a lot is shown`() {
        val waiting = SchoolsJump("9002")
        for (state in listOf(loaded("9003"), MapUiState(), MapUiState("9002", SuburbCardState.Failed("9002", "x")), MapUiState(selectedLotId = "1", lotCard = LotCardState.Loading("1", null)))) {
            val decision = waiting.onState(state)
            assertFalse(decision.go, "$state")
            assertNull(decision.next.pending, "$state")
        }
    }

    @Test
    fun `with nothing pending a new state does nothing`() {
        assertEquals(JumpDecision(SchoolsJump(), go = false), SchoolsJump().onState(loaded("9001")))
    }

    @Test
    fun `opening the Schools group keeps the other groups as they were`() {
        assertEquals(mapOf("Crime" to false, CardGroups.SCHOOLS_TITLE to true), SchoolsJump.opened(mapOf("Crime" to false, CardGroups.SCHOOLS_TITLE to false)))
        assertEquals("Schools", SchoolsJump.GROUP_TITLE)
        assertTrue(CardGroups.SUBURB.any { it.title == SchoolsJump.GROUP_TITLE })
    }

    @Test
    fun `the scroll puts the group just under the pinned header, never above the top`() {
        assertEquals(300 + 500 - 120 - SchoolsJump.GAP_PX, SchoolsJump.scrollTop(currentTop = 300, groupOffset = 500, stickyHeight = 120))
        assertEquals(0, SchoolsJump.scrollTop(currentTop = 0, groupOffset = 40, stickyHeight = 120))
    }
}
