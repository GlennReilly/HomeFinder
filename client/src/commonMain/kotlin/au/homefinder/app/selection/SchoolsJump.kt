package au.homefinder.app.selection

/** What a [SchoolsJump] decided: the jump to carry into the next state, and whether to open and scroll to the Schools group now. */
data class JumpDecision(val next: SchoolsJump, val go: Boolean)

/**
 * A wish to show a suburb's Schools group (ADR 0076), from a click on its NAPLAN label or the card's "Jump to Schools" button. The suburb may still be
 * loading, so the wish waits until its card is loaded, then is carried out once; it is dropped when something else is selected or the card fails.
 */
data class SchoolsJump(val pending: String? = null) {
    /**
     * Asks to jump to [suburbId]'s Schools group while the page shows [state]. When that suburb's card is already loaded it goes at once; otherwise it
     * waits for the state that brings it (a different suburb on show now is the one being replaced, so it is not a reason to give up).
     */
    fun requested(suburbId: String, state: MapUiState): JumpDecision =
        if (ready(suburbId, state)) JumpDecision(SchoolsJump(), go = true) else JumpDecision(SchoolsJump(suburbId), go = false)

    /** A new state arrived: go when it is the waited-for suburb's loaded card, keep waiting while it loads, and give up on anything else. */
    fun onState(state: MapUiState): JumpDecision {
        val suburbId = pending ?: return JumpDecision(this, go = false)
        return when {
            ready(suburbId, state) -> JumpDecision(SchoolsJump(), go = true)
            state.selectedSuburbId == suburbId && state.lotCard is LotCardState.Hidden && state.card is SuburbCardState.Loading -> JumpDecision(this, go = false)
            else -> JumpDecision(SchoolsJump(), go = false)
        }
    }

    private fun ready(suburbId: String, state: MapUiState): Boolean =
        state.selectedSuburbId == suburbId && state.lotCard is LotCardState.Hidden && state.card is SuburbCardState.Loaded

    companion object {
        /** The group to open. */
        const val GROUP_TITLE: String = CardGroups.SCHOOLS_TITLE

        /** Space left between the pinned card header and the top of the Schools group, in CSS pixels. */
        const val GAP_PX: Int = 8

        /** [groupOpen] with the Schools group open, the rest as they were. */
        fun opened(groupOpen: Map<String, Boolean>): Map<String, Boolean> = groupOpen + (GROUP_TITLE to true)

        /**
         * Where to scroll the card so the Schools group sits just below the pinned header: [currentTop] is the card's scroll position, [groupOffset] how
         * far the group's top is below the card's top edge right now, and [stickyHeight] the height of the pinned header. Never above the top.
         */
        fun scrollTop(currentTop: Int, groupOffset: Int, stickyHeight: Int): Int = (currentTop + groupOffset - stickyHeight - GAP_PX).coerceAtLeast(0)
    }
}
