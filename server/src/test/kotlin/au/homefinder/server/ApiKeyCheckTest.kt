package au.homefinder.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiKeyCheckTest {
    private val key = "0123456789abcdef"

    @Test
    fun `the exact key matches`() {
        assertTrue(ApiKeyCheck.matches(key, key))
    }

    @Test
    fun `a missing, empty or different key does not match`() {
        assertFalse(ApiKeyCheck.matches(null, key))
        assertFalse(ApiKeyCheck.matches("", key))
        assertFalse(ApiKeyCheck.matches("0123456789abcdeX", key))
    }

    @Test
    fun `prefixes, extensions and other case do not match`() {
        assertFalse(ApiKeyCheck.matches("0123456789abcde", key))
        assertFalse(ApiKeyCheck.matches(key + "0", key))
        assertFalse(ApiKeyCheck.matches(key.uppercase(), key))
    }
}
