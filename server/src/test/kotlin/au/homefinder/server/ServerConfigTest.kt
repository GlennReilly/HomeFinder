package au.homefinder.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServerConfigTest {
    private val goodKey = "0123456789abcdef"

    private fun load(vararg env: Pair<String, String>) = ServerConfigLoader.fromEnv(mapOf(*env))

    @Test
    fun `defaults are all interfaces on port 8080`() {
        val config = assertIs<ConfigResult.Loaded>(load("API_KEY" to goodKey)).config
        assertEquals(ServerConfig("0.0.0.0", 8080, goodKey), config)
    }

    @Test
    fun `host port and key can be set`() {
        val config = assertIs<ConfigResult.Loaded>(
            load("API_HOST" to "127.0.0.1", "API_PORT" to "9090", "API_KEY" to goodKey),
        ).config
        assertEquals("127.0.0.1", config.host)
        assertEquals(9090, config.port)
    }

    @Test
    fun `blank host and port fall back to the defaults and values are trimmed`() {
        val config = assertIs<ConfigResult.Loaded>(
            load("API_HOST" to "  ", "API_PORT" to " ", "API_KEY" to "  $goodKey  "),
        ).config
        assertEquals(ServerConfig("0.0.0.0", 8080, goodKey), config)
    }

    @Test
    fun `a missing key is rejected`() {
        val problems = assertIs<ConfigResult.Invalid>(load()).problems
        assertEquals(listOf("API_KEY must be set"), problems)
    }

    @Test
    fun `a short key is rejected`() {
        val problems = assertIs<ConfigResult.Invalid>(load("API_KEY" to "short")).problems
        assertTrue(problems.single().contains("at least 16 characters"))
    }

    @Test
    fun `bad ports are rejected`() {
        listOf("abc", "0", "-1", "65536", "80.5").forEach { port ->
            val problems = assertIs<ConfigResult.Invalid>(load("API_PORT" to port, "API_KEY" to goodKey)).problems
            assertTrue(problems.single().contains("API_PORT"), "port '$port'")
        }
        assertIs<ConfigResult.Loaded>(load("API_PORT" to "1", "API_KEY" to goodKey))
        assertIs<ConfigResult.Loaded>(load("API_PORT" to "65535", "API_KEY" to goodKey))
    }

    @Test
    fun `every problem is reported at once`() {
        val problems = assertIs<ConfigResult.Invalid>(load("API_PORT" to "nope")).problems
        assertEquals(2, problems.size)
    }

    @Test
    fun `the key never appears when the config is printed`() {
        val text = ServerConfig("0.0.0.0", 8080, goodKey).toString()
        assertFalse(text.contains(goodKey))
        assertTrue(text.contains("port=8080"))
    }
}
