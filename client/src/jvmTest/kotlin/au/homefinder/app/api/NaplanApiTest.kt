package au.homefinder.app.api

import au.homefinder.shared.model.NaplanEntry
import au.homefinder.shared.model.NaplanScore
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NaplanApiTest {
    private class Writes(private val result: HttpResult) : HttpWrite {
        val calls = mutableListOf<List<Any?>>()
        override suspend fun send(method: HttpVerb, url: String, headers: Map<String, String>, body: String?): HttpResult =
            result.also { calls += listOf(method, url, headers, body) }
    }

    private val config = ApiConfig("http://host:8080/", "secret-key-123456")

    @Test
    fun `saving sends a PUT with the key only in a header and a JSON body`() = runBlocking<Unit> {
        val writes = Writes(HttpResult.Response(200, """{"score":512,"year":2025,"updatedOn":"2026-11-08"}"""))
        val result = NaplanApi(config, writes).save("1234", NaplanEntry(512, 2025))
        assertEquals(LookupResult.Found(NaplanScore(512, 2025, LocalDate(2026, 11, 8))), result)
        val (method, url, headers, body) = writes.calls.single()
        assertEquals(HttpVerb.PUT, method)
        assertEquals("http://host:8080/v1/schools/1234/naplan", url)
        assertEquals(mapOf("X-API-Key" to "secret-key-123456"), headers)
        assertEquals("""{"score":512,"year":2025}""", body)
        assertTrue("secret" !in (url as String))
    }

    @Test
    fun `the school code is encoded into the path`() = runBlocking<Unit> {
        val writes = Writes(HttpResult.Response(404, ""))
        assertEquals(LookupResult.NotFound, NaplanApi(config, writes).save("a/b c", NaplanEntry(512, 2025)))
        assertEquals("http://host:8080/v1/schools/a%2Fb%20c/naplan", writes.calls.single()[1])
    }

    @Test
    fun `save answers are told apart`() = runBlocking<Unit> {
        suspend fun save(result: HttpResult) = NaplanApi(config, Writes(result)).save("1234", NaplanEntry(512, 2025))
        assertEquals(LookupResult.Unauthorized, save(HttpResult.Response(401, "")))
        assertIs<LookupResult.Failed>(save(HttpResult.Response(400, "")))
        assertIs<LookupResult.Failed>(save(HttpResult.Response(503, "")))
        assertIs<LookupResult.Failed>(save(HttpResult.Response(200, "not json")))
        assertIs<LookupResult.Failed>(save(HttpResult.Failure("offline")))
    }

    @Test
    fun `removing sends a DELETE and accepts no content`() = runBlocking<Unit> {
        val writes = Writes(HttpResult.Response(204, ""))
        assertEquals(LookupResult.Found(Unit), NaplanApi(config, writes).remove("1234"))
        assertEquals(listOf(HttpVerb.DELETE, "http://host:8080/v1/schools/1234/naplan"), writes.calls.single().take(2))
        assertEquals(LookupResult.Unauthorized, NaplanApi(config, Writes(HttpResult.Response(401, ""))).remove("1234"))
        assertIs<LookupResult.Failed>(NaplanApi(config, Writes(HttpResult.Failure("offline"))).remove("1234"))
    }
}
