package au.homefinder.app

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopApiConfigTest {
    @Test
    fun `defaults to this machine on the default port with no key`() {
        val config = desktopApiConfig(emptyMap())
        assertEquals("http://localhost:8080", config.baseUrl)
        assertEquals("", config.apiKey)
    }

    @Test
    fun `reads the url and key from the environment`() {
        val config = desktopApiConfig(mapOf("HOMEFINDER_API_URL" to "http://192.168.86.51:8080", "HOMEFINDER_API_KEY" to "abc"))
        assertEquals("http://192.168.86.51:8080", config.baseUrl)
        assertEquals("abc", config.apiKey)
    }
}
