package au.homefinder.app.api

import au.homefinder.shared.model.SuburbFilterValue
import au.homefinder.shared.model.SuburbFiltersResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FilterApiTest {
    private class Reads(private val result: HttpResult) : HttpGet {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun get(url: String, headers: Map<String, String>): HttpResult = result.also { calls += url to headers }
    }

    private val config = ApiConfig("http://host:8080/", "secret-key-123456")

    @Test
    fun `values are fetched with the key in a header only`() = runBlocking<Unit> {
        val reads = Reads(HttpResult.Response(200, """{"suburbs":[{"suburbId":"9001","averageIcsea":1032},{"suburbId":"9002"}],"icseaSource":"S"}"""))
        val result = FilterApi(config, reads).values()
        assertEquals(
            LookupResult.Found(SuburbFiltersResponse(listOf(SuburbFilterValue("9001", 1032), SuburbFilterValue("9002", null)), "S", null)),
            result,
        )
        assertEquals("http://host:8080/v1/suburbs/filters", reads.calls.single().first)
        assertEquals(mapOf("X-API-Key" to "secret-key-123456"), reads.calls.single().second)
    }

    @Test
    fun `a wrong key, a failure and an unreadable answer are reported`() = runBlocking<Unit> {
        assertEquals(LookupResult.Unauthorized, FilterApi(config, Reads(HttpResult.Response(401, ""))).values())
        assertIs<LookupResult.Failed>(FilterApi(config, Reads(HttpResult.Response(503, ""))).values())
        assertIs<LookupResult.Failed>(FilterApi(config, Reads(HttpResult.Response(200, "nope"))).values())
        assertIs<LookupResult.Failed>(FilterApi(config, Reads(HttpResult.Failure("offline"))).values())
    }
}
