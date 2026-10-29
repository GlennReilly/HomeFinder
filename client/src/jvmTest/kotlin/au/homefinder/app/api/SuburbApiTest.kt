package au.homefinder.app.api

import au.homefinder.shared.api.SuburbRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SuburbApiTest {
    private class Recorder(private val result: HttpResult) : HttpGet {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
            calls += url to headers
            return result
        }
    }

    private val config = ApiConfig("http://host:8080/", "secret-key-123456")
    private val refJson = """{"suburbId":"9001","name":"HAMILTON","postcode":"2303"}"""

    private fun at(result: HttpResult, lat: Double = -32.92, lon: Double = 151.75): Pair<LookupResult<SuburbRef>, Recorder> {
        val recorder = Recorder(result)
        return runBlocking { SuburbApi(config, recorder).suburbAt(lat, lon) } to recorder
    }

    @Test
    fun `a tap lookup sends plain decimal coordinates and the key only as a header`() {
        val (_, recorder) = at(HttpResult.Response(200, refJson))
        val (url, headers) = recorder.calls.single()
        assertEquals("http://host:8080/v1/suburbs/at?lat=-32.9200000&lon=151.7500000", url)
        assertEquals(mapOf("X-API-Key" to "secret-key-123456"), headers)
        assertFalse(url.contains("secret"))
    }

    @Test
    fun `a 200 answer is decoded`() {
        val result = at(HttpResult.Response(200, refJson)).first
        assertEquals(LookupResult.Found(SuburbRef("9001", "HAMILTON", "2303")), result)
    }

    @Test
    fun `404 is not found and 401 is unauthorized`() {
        assertEquals(LookupResult.NotFound, at(HttpResult.Response(404, """{"error":"not_found"}""")).first)
        assertEquals(LookupResult.Unauthorized, at(HttpResult.Response(401, """{"error":"unauthorized"}""")).first)
    }

    @Test
    fun `other statuses, network failures and unreadable bodies are failures`() {
        assertEquals(LookupResult.Failed("the server answered 503"), at(HttpResult.Response(503, "")).first)
        assertEquals(LookupResult.Failed("timeout"), at(HttpResult.Failure("timeout")).first)
        assertIs<LookupResult.Failed>(at(HttpResult.Response(200, "<html>")).first)
        assertIs<LookupResult.Failed>(at(HttpResult.Response(200, """{"unexpected":true}""")).first)
    }

    @Test
    fun `a report is requested by id with the id encoded`() {
        val recorder = Recorder(HttpResult.Response(404, ""))
        runBlocking { SuburbApi(config, recorder).report("9001/../v1/status") }
        assertEquals("http://host:8080/v1/suburbs/9001%2F..%2Fv1%2Fstatus", recorder.calls.single().first)
    }

    @Test
    fun `coordinates are formatted to seven places without scientific notation or negative zero`() {
        assertEquals("0.0000000", SuburbApi.formatCoordinate(0.0))
        assertEquals("0.0000000", SuburbApi.formatCoordinate(-0.00000001))
        assertEquals("0.0000000", SuburbApi.formatCoordinate(1e-9))
        assertEquals("151.7512346", SuburbApi.formatCoordinate(151.7512345678))
        assertEquals("-33.0000000", SuburbApi.formatCoordinate(-33.0))
        assertEquals("90.0000000", SuburbApi.formatCoordinate(90.0))
        assertEquals("-0.0500000", SuburbApi.formatCoordinate(-0.05))
        assertTrue(listOf(1e-7, 123456.789, -179.9999999).map(SuburbApi::formatCoordinate).none { it.contains('E') })
    }

    @Test
    fun `path segments keep unreserved characters and encode everything else`() {
        assertEquals("9001", SuburbApi.encodePathSegment("9001"))
        assertEquals("a-b._c~", SuburbApi.encodePathSegment("a-b._c~"))
        assertEquals("a%2Fb", SuburbApi.encodePathSegment("a/b"))
        assertEquals("9001%27%20OR%20%271%27%3D%271", SuburbApi.encodePathSegment("9001' OR '1'='1"))
        assertEquals("%C3%A9", SuburbApi.encodePathSegment("é"))
        assertEquals("", SuburbApi.encodePathSegment(""))
    }
}
