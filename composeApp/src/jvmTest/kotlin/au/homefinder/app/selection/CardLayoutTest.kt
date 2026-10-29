package au.homefinder.app.selection

import kotlin.test.Test
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardLayoutTest {
    @Test
    fun `narrow windows use a bottom sheet and wide ones a side panel`() {
        assertEquals(CardLayout.BOTTOM_SHEET, cardLayoutFor(360f))
        assertEquals(CardLayout.BOTTOM_SHEET, cardLayoutFor(599.9f))
        assertEquals(CardLayout.SIDE_PANEL, cardLayoutFor(600f))
        assertEquals(CardLayout.SIDE_PANEL, cardLayoutFor(1280f))
    }

    @Test
    fun `the side panel is narrower than the smallest window that gets one`() {
        assert(SIDE_PANEL_WIDTH_DP < SIDE_PANEL_MIN_WIDTH_DP)
    }

    @Test
    fun `escape clears the selection on key down only when something is selected`() {
        assertTrue(au.homefinder.app.clearsSelection(KeyEventType.KeyDown, Key.Escape, hasSelection = true))
        assertFalse(au.homefinder.app.clearsSelection(KeyEventType.KeyDown, Key.Escape, hasSelection = false))
        assertFalse(au.homefinder.app.clearsSelection(KeyEventType.KeyUp, Key.Escape, hasSelection = true))
        assertFalse(au.homefinder.app.clearsSelection(KeyEventType.KeyDown, Key.Enter, hasSelection = true))
    }
}
