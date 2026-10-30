package au.homefinder.app.api

import au.homefinder.shared.model.LotRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LotApiTest {
    private class Recorder(private val result: HttpResult) : HttpGet {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun get(url: String, headers: Map<String, String>): HttpResult = result.also { calls += url to headers }
    }

    private val config = ApiConfig("http://host:8080/", "secret-key-123456")

    @Test
    fun `a lot lookup sends plain decimal coordinates and the key only as a header`() = runBlocking<Unit> {
        val recorder = Recorder(HttpResult.Response(200, """{"lotId":"1001","suburbId":"9001"}"""))
        val result = LotApi(config, recorder).lotAt(-32.92, 151.75)
        assertEquals(LookupResult.Found(LotRef("1001", "9001")), result)
        val (url, headers) = recorder.calls.single()
        assertEquals("http://host:8080/v1/lots/at?lat=-32.9200000&lon=151.7500000", url)
        assertEquals(mapOf("X-API-Key" to "secret-key-123456"), headers)
        assertTrue("secret" !in url)
    }

    @Test
    fun `a report is requested by id with the id encoded`() = runBlocking<Unit> {
        val recorder = Recorder(HttpResult.Response(404, ""))
        assertEquals(LookupResult.NotFound, LotApi(config, recorder).report("10/05 x"))
        assertEquals("http://host:8080/v1/lots/10%2F05%20x", recorder.calls.single().first)
    }

    @Test
    fun `401 is unauthorized and other trouble is a failure`() = runBlocking<Unit> {
        assertEquals(LookupResult.Unauthorized, LotApi(config, Recorder(HttpResult.Response(401, ""))).lotAt(0.0, 0.0))
        assertIs<LookupResult.Failed>(LotApi(config, Recorder(HttpResult.Response(500, ""))).report("1"))
        assertIs<LookupResult.Failed>(LotApi(config, Recorder(HttpResult.Failure("offline"))).report("1"))
        assertIs<LookupResult.Failed>(LotApi(config, Recorder(HttpResult.Response(200, "not json"))).report("1"))
    }
}
