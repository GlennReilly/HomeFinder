package au.homefinder.app

import kotlin.test.Test
import kotlin.test.assertEquals

class AppTitleTest {
    @Test
    fun `app title comes from shared app info`() {
        assertEquals("Home Finder", appTitle())
    }
}
