package au.homefinder.app.api

import au.homefinder.shared.model.HomelyRating
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RatingApiTest {
    private class Reads(private val result: HttpResult) : HttpGet {
        val urls = mutableListOf<String>()
        override suspend fun get(url: String, headers: Map<String, String>): HttpResult = result.also { urls += url }
    }

    private class Writes(private val result: HttpResult) : HttpWrite {
        val calls = mutableListOf<List<Any?>>()
        override suspend fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpResult =
            result.also { calls += listOf(method, url, headers, body) }
    }

    private val config = ApiConfig("http://host:8080/", "secret-key-123456")
    private fun api(write: HttpResult, read: HttpResult = HttpResult.Response(200, """{"ratings":[]}""")) = Triple(RatingApi(config, Reads(read), Writes(write)), Reads(read), Writes(write))

    @Test
    fun `saving sends a PUT with the key only in a header and a JSON body`() = runBlocking<Unit> {
        val writes = Writes(HttpResult.Response(200, """{"stars":2.1,"reviewCount":7}"""))
        val result = RatingApi(config, Reads(HttpResult.Response(200, "")), writes).save("9001", HomelyRating(2.1, 7))
        assertEquals(LookupResult.Found(HomelyRating(2.1, 7)), result)
        val (method, url, headers, body) = writes.calls.single()
        assertEquals("PUT", method)
        assertEquals("http://host:8080/v1/suburbs/9001/rating", url)
        assertEquals(mapOf("X-API-Key" to "secret-key-123456"), headers)
        assertEquals("""{"stars":2.1,"reviewCount":7}""", body)
        assertTrue("secret" !in (url as String))
    }

    @Test
    fun `the suburb id is encoded into the path`() = runBlocking<Unit> {
        val writes = Writes(HttpResult.Response(404, ""))
        assertEquals(LookupResult.NotFound, RatingApi(config, Reads(HttpResult.Response(200, "")), writes).save("a/b c", HomelyRating(1.0)))
        assertEquals("http://host:8080/v1/suburbs/a%2Fb%20c/rating", writes.calls.single()[1])
    }

    @Test
    fun `save answers are told apart`() = runBlocking<Unit> {
        fun save(result: HttpResult) = runBlocking { RatingApi(config, Reads(HttpResult.Response(200, "")), Writes(result)).save("9001", HomelyRating(1.0)) }
        assertEquals(LookupResult.Unauthorized, save(HttpResult.Response(401, "")))
        assertIs<LookupResult.Failed>(save(HttpResult.Response(400, "")))
        assertIs<LookupResult.Failed>(save(HttpResult.Response(503, "")))
        assertIs<LookupResult.Failed>(save(HttpResult.Failure("offline")))
        assertIs<LookupResult.Failed>(save(HttpResult.Response(200, "nope")))
    }

    @Test
    fun `removing sends a DELETE with no body and treats 204 as done`() = runBlocking<Unit> {
        val writes = Writes(HttpResult.Response(204, ""))
        assertEquals(LookupResult.Found(Unit), RatingApi(config, Reads(HttpResult.Response(200, "")), writes).remove("9001"))
        val (method, url, _, body) = writes.calls.single()
        assertEquals("DELETE", method)
        assertEquals("http://host:8080/v1/suburbs/9001/rating", url)
        assertEquals(null, body)
        assertEquals(LookupResult.Unauthorized, RatingApi(config, Reads(HttpResult.Response(200, "")), Writes(HttpResult.Response(401, ""))).remove("9001"))
        assertIs<LookupResult.Failed>(RatingApi(config, Reads(HttpResult.Response(200, "")), Writes(HttpResult.Response(500, ""))).remove("9001"))
    }

    @Test
    fun `the list is read with a GET`() = runBlocking<Unit> {
        val body = """{"ratings":[{"suburbId":"9001","name":"HAMILTON","rating":{"stars":2.1,"reviewCount":7},"latitude":-32.92,"longitude":151.75}]}"""
        val reads = Reads(HttpResult.Response(200, body))
        val found = RatingApi(config, reads, Writes(HttpResult.Response(200, ""))).all()
        assertEquals("HAMILTON", assertIs<LookupResult.Found<au.homefinder.shared.model.SuburbRatingsResponse>>(found).value.ratings.single().name)
        assertEquals(listOf("http://host:8080/v1/suburbs/ratings"), reads.urls)
    }
}
