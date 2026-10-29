package au.homefinder.app.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiStatusCheckerTest {
    private class Call(val url: String, val headers: Map<String, String>)

    private class FakeHttp(private val answers: Map<String, HttpResult>) : HttpGet {
        val calls = mutableListOf<Call>()
        override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
            calls += Call(url, headers)
            return answers.entries.firstOrNull { url.endsWith(it.key) }?.value ?: HttpResult.Failure("no answer")
        }
    }

    private val config = ApiConfig("http://host:8080/", "secret-key-123456")
    private val healthOk = HttpResult.Response(200, """{"status":"ok","service":"Home Finder API","version":"0.1.0"}""")
    private val statusOk = HttpResult.Response(200, """{"authenticated":true}""")

    private fun check(config: ApiConfig = this.config, vararg answers: Pair<String, HttpResult>): Pair<ApiStatus, FakeHttp> {
        val http = FakeHttp(mapOf(*answers))
        return runBlocking { ApiStatusChecker(config, http).check() } to http
    }

    @Test
    fun `online when health is ok and the key is accepted`() {
        val (status, _) = check(config, "/health" to healthOk, "/v1/status" to statusOk)
        assertEquals(ApiStatus.Online("0.1.0"), status)
    }

    @Test
    fun `unreachable when the server cannot be contacted`() {
        val (status, http) = check(config, "/health" to HttpResult.Failure("connection refused"))
        assertEquals(ApiStatus.Unreachable("connection refused"), status)
        assertEquals(1, http.calls.size)
    }

    @Test
    fun `a rejected key is reported`() {
        val (status, _) = check(config, "/health" to healthOk, "/v1/status" to HttpResult.Response(401, """{"error":"unauthorized"}"""))
        assertEquals(ApiStatus.KeyRejected, status)
    }

    @Test
    fun `a missing key is reported without calling the authenticated endpoint`() {
        val (status, http) = check(config.copy(apiKey = "  "), "/health" to healthOk)
        assertEquals(ApiStatus.KeyMissing, status)
        assertEquals(1, http.calls.size)
    }

    @Test
    fun `an unexpected health status or body is a bad response`() {
        assertTrue(check(config, "/health" to HttpResult.Response(500, "oops")).first is ApiStatus.BadResponse)
        assertTrue(check(config, "/health" to HttpResult.Response(200, "<html>")).first is ApiStatus.BadResponse)
    }

    @Test
    fun `an unexpected status code is a bad response`() {
        val (status, _) = check(config, "/health" to healthOk, "/v1/status" to HttpResult.Response(503, ""))
        assertEquals(ApiStatus.BadResponse("status returned 503"), status)
    }

    @Test
    fun `a network failure on the second request is unreachable`() {
        val (status, _) = check(config, "/health" to healthOk, "/v1/status" to HttpResult.Failure("timeout"))
        assertEquals(ApiStatus.Unreachable("timeout"), status)
    }

    @Test
    fun `the key is sent only to the authenticated endpoint`() {
        val (_, http) = check(config, "/health" to healthOk, "/v1/status" to statusOk)
        assertEquals(emptyMap(), http.calls[0].headers)
        assertEquals(mapOf("X-API-Key" to "secret-key-123456"), http.calls[1].headers)
    }

    @Test
    fun `urls join without doubled slashes`() {
        assertEquals("http://host:8080/health", config.url("/health"))
        assertEquals("http://host:8080/v1/status", ApiConfig("http://host:8080", "k").url("v1/status"))
    }

    @Test
    fun `the key is never printed`() {
        assertFalse(config.toString().contains("secret-key"))
    }

    @Test
    fun `every status has a readable label`() {
        val labels = listOf(
            ApiStatus.Checking, ApiStatus.Online("0.1.0"), ApiStatus.KeyMissing, ApiStatus.KeyRejected,
            ApiStatus.Unreachable("x"), ApiStatus.BadResponse("y"),
        ).map { it.label() }
        assertTrue(labels.all { it.startsWith("API:") })
        assertEquals(labels.size, labels.toSet().size)
    }
}
