package au.homefinder.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class AppInfoTest {
    @Test
    fun `prices are displayed in AUD`() {
        assertEquals("AUD", AppInfo.CURRENCY_CODE)
    }
}
