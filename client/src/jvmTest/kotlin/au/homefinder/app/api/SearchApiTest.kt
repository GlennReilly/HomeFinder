package au.homefinder.app.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SearchApiTest {
    private class Recorder(private val result: HttpResult) : HttpGet {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun get(url: String, headers: Map<String, String>): HttpResult = result.also { calls += url to headers }
    }

    private val config = ApiConfig("http://host:8080/", "secret-key-123456")

    @Test
    fun `the text is percent-encoded into q and the key travels only as a header`() = runBlocking<Unit> {
        val recorder = Recorder(HttpResult.Response(200, """{"results":[]}"""))
        val result = SearchApi(config, recorder).search("3/12 O'Connell St&x=1")
        assertEquals(emptyList(), assertIs<LookupResult.Found<*>>(result).let { (it.value as au.homefinder.shared.model.SearchResponse).results })
        val (url, headers) = recorder.calls.single()
        assertEquals("http://host:8080/v1/search?q=3%2F12%20O%27Connell%20St%26x%3D1", url)
        assertEquals(mapOf("X-API-Key" to "secret-key-123456"), headers)
        assertTrue("secret" !in url)
    }

    @Test
    fun `results are decoded and trouble is reported`() = runBlocking<Unit> {
        val body = """{"results":[{"kind":"SUBURB","id":"9001","label":"HAMILTON","detail":"Suburb, NSW 2303","latitude":-32.92,"longitude":151.75,"bounds":{"west":151.74,"south":-32.93,"east":151.76,"north":-32.91}}]}"""
        val found = SearchApi(config, Recorder(HttpResult.Response(200, body))).search("ham")
        assertEquals("HAMILTON", assertIs<LookupResult.Found<au.homefinder.shared.model.SearchResponse>>(found).value.results.single().label)
        assertEquals(LookupResult.Unauthorized, SearchApi(config, Recorder(HttpResult.Response(401, ""))).search("ham"))
        assertIs<LookupResult.Failed>(SearchApi(config, Recorder(HttpResult.Response(503, ""))).search("ham"))
        assertIs<LookupResult.Failed>(SearchApi(config, Recorder(HttpResult.Failure("offline"))).search("ham"))
        assertIs<LookupResult.Failed>(SearchApi(config, Recorder(HttpResult.Response(200, "nope"))).search("ham"))
    }
}
