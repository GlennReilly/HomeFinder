package au.homefinder.app.api

import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.SuburbExportRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExportApiTest {
    private class Fetches(private val result: BytesResult) : HttpBytes {
        val calls = mutableListOf<List<Any?>>()
        override suspend fun fetch(method: HttpVerb, url: String, headers: Map<String, String>, body: String?, timeoutMillis: Long): BytesResult =
            result.also { calls += listOf(method, url, headers, body, timeoutMillis) }
    }

    private val config = ApiConfig("http://host:8080/", "secret-key-123456")
    private val request = SuburbExportRequest(listOf("9001", "9002"), listOf("Homes rented: At most 45%"), "+Parks")
    private val pdf = "%PDF-1.7 bytes".encodeToByteArray()

    @Test
    fun `it POSTs the request as JSON with the key in a header and a long timeout, and returns the bytes`() = runBlocking<Unit> {
        val fetches = Fetches(BytesResult.Response(200, pdf))
        val result = ExportApi(config, fetches).suburbsPdf(request)
        assertIs<LookupResult.Found<ByteArray>>(result)
        assertContentEquals(pdf, result.value)
        val (method, url, headers, body, timeout) = fetches.calls.single()
        assertEquals(HttpVerb.POST, method)
        assertEquals("http://host:8080/v1/export/suburbs.pdf", url)
        assertEquals(mapOf("X-API-Key" to "secret-key-123456"), headers)
        assertEquals(request, ApiJson.decodeFromString<SuburbExportRequest>(body as String))
        assertEquals(EXPORT_TIMEOUT_MILLIS, timeout)
    }

    @Test
    fun `a 400 says whether the list was too long or empty`() = runBlocking<Unit> {
        val tooMany = ExportApi(config, Fetches(BytesResult.Response(400, """{"error":"too_many_suburbs"}""".encodeToByteArray()))).suburbsPdf(request)
        assertEquals(LookupResult.Failed(EXPORT_TOO_MANY_MESSAGE), tooMany)
        val empty = ExportApi(config, Fetches(BytesResult.Response(400, """{"error":"invalid_export"}""".encodeToByteArray()))).suburbsPdf(request)
        assertEquals(LookupResult.Failed(EXPORT_EMPTY_MESSAGE), empty)
        val unreadable = ExportApi(config, Fetches(BytesResult.Response(400, "not json".encodeToByteArray()))).suburbsPdf(request)
        assertEquals(LookupResult.Failed(EXPORT_EMPTY_MESSAGE), unreadable)
    }

    @Test
    fun `other answers map to their results`() = runBlocking<Unit> {
        suspend fun answer(result: BytesResult) = ExportApi(config, Fetches(result)).suburbsPdf(request)
        assertEquals(LookupResult.Unauthorized, answer(BytesResult.Response(401, ByteArray(0))))
        assertEquals(LookupResult.Failed(EXPORT_NOT_FOUND_MESSAGE), answer(BytesResult.Response(404, ByteArray(0))))
        assertEquals(LookupResult.Failed("the server answered 503"), answer(BytesResult.Response(503, ByteArray(0))))
        assertEquals(LookupResult.Failed("timed out"), answer(BytesResult.Failure("timed out")))
    }
}
