package au.homefinder.app.selection

import au.homefinder.app.filter.FilterResult
import au.homefinder.app.filter.FilterSelection
import kotlin.test.Test
import kotlin.test.assertEquals

class SidePanelTest {
    @Test
    fun `the panel opens on Filters, folded only on a narrow window`() {
        assertEquals(SidePanelState(SideTab.FILTERS, collapsed = false), SidePanelState.initial(startFolded = false))
        assertEquals(SidePanelState(SideTab.FILTERS, collapsed = true), SidePanelState.initial(startFolded = true))
    }

    @Test
    fun `tapping another tab opens it, tapping the open tab folds and unfolds the panel`() {
        val onFilters = SidePanelState()
        val suburb = onFilters.tapped(SideTab.SUBURB)
        assertEquals(SidePanelState(SideTab.SUBURB, false), suburb)
        val folded = suburb.tapped(SideTab.SUBURB)
        assertEquals(SidePanelState(SideTab.SUBURB, true), folded)
        assertEquals(SidePanelState(SideTab.SUBURB, false), folded.tapped(SideTab.SUBURB))
        assertEquals(SidePanelState(SideTab.FILTERS, false), folded.tapped(SideTab.FILTERS), "another tab unfolds the panel")
    }

    @Test
    fun `selecting shows the card even from a folded panel, and clearing goes back to Filters`() {
        assertEquals(SidePanelState(SideTab.SUBURB, false), SidePanelState.initial(true).cardOpened())
        assertEquals(SidePanelState(SideTab.FILTERS, false), SidePanelState(SideTab.SUBURB).cardClosed(startFolded = false))
        assertEquals(SidePanelState(SideTab.FILTERS, true), SidePanelState(SideTab.SUBURB).cardClosed(startFolded = true))
    }

    @Test
    fun `the Filters tab shows the match count only while a filter is on`() {
        val none = FilterResult(FilterSelection(), passingIds = null, total = 173, unknown = emptyMap())
        val some = FilterResult(FilterSelection(), passingIds = setOf("a", "b"), total = 173, unknown = emptyMap())
        assertEquals("Filters", filtersTabLabel(none))
        assertEquals("Filters", filtersTabLabel(null))
        assertEquals("Filters (2 of 173)", filtersTabLabel(some))
    }
}
