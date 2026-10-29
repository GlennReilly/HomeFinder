package au.homefinder.server

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerInfoTest {
    @Test
    fun `server name is derived from shared app info`() {
        assertEquals("Home Finder API", ServerInfo.name)
    }
}
