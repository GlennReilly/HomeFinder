package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.GeoBounds
import au.homefinder.shared.model.SearchResponse
import au.homefinder.shared.model.SearchResult
import au.homefinder.shared.model.SearchResultKind
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchRoutesTest {
    private val key = "0123456789abcdef"
    private val config = ServerConfig("127.0.0.1", 8080, key)
    private val suburb = SearchResult(SearchResultKind.SUBURB, "9001", "HAMILTON", "Suburb, NSW 2303", -32.92, 151.75, GeoBounds(151.74, -32.93, 151.76, -32.91))
    private val address = SearchResult(SearchResultKind.ADDRESS, "GANSW1", "12 MAIN STREET, HAMILTON NSW 2303", null, -32.9201, 151.7501, lotId = "1001")

    private class FakeIndex(val results: List<SearchResult> = emptyList(), val failure: Exception? = null) : SearchIndex {
        val calls = mutableListOf<Pair<List<String>, Int>>()
        override suspend fun search(query: SearchQuery, limit: Int): List<SearchResult> {
            calls += query.words to limit
            failure?.let { throw it }
            return results
        }
    }

    private fun api(index: SearchIndex?, block: suspend HttpClient.() -> Unit) = testApplication {
        application { module(config, search = index) }
        client.block()
    }

    private suspend fun HttpClient.search(q: String, withKey: Boolean = true): HttpResponse =
        get("${ApiContract.SEARCH_PATH}?q=$q") { if (withKey) header(ApiContract.API_KEY_HEADER, key) }

    @Test
    fun `a query returns the suggestions as JSON`() = FakeIndex(listOf(suburb, address)).let { fake ->
        api(fake) {
            val response = search("ham")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf(suburb, address), ApiJson.decodeFromString<SearchResponse>(response.bodyAsText()).results)
            assertEquals(listOf(listOf("ham") to ApiContract.SEARCH_MAX_RESULTS), fake.calls)
        }
    }

    @Test
    fun `the query is trimmed and reduced to words before it reaches the index`() = FakeIndex().let { fake ->
        api(fake) {
            search("%20%203/12%20Main%20St%20")
            assertEquals(listOf("3", "12", "main", "st"), fake.calls.single().first)
        }
    }

    @Test
    fun `a missing, too short or too long query is a bad request without asking the index`() = FakeIndex().let { fake ->
        api(fake) {
            listOf("", "a", "%20%20%20").forEach { assertEquals(HttpStatusCode.BadRequest, search(it).status, it) }
            assertEquals(HttpStatusCode.BadRequest, get(ApiContract.SEARCH_PATH) { header(ApiContract.API_KEY_HEADER, key) }.status)
            assertEquals(HttpStatusCode.BadRequest, search("a".repeat(ApiContract.SEARCH_MAX_QUERY_LENGTH + 1)).status)
            assertEquals(ApiError(ApiError.INVALID_QUERY), ApiJson.decodeFromString<ApiError>(search("a").bodyAsText()))
            assertTrue(fake.calls.isEmpty())
        }
    }

    @Test
    fun `a query with no letters or digits reaches the index as no words`() = FakeIndex().let { fake ->
        api(fake) {
            val response = search("---")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(emptyList(), ApiJson.decodeFromString<SearchResponse>(response.bodyAsText()).results)
            assertEquals(listOf(emptyList<String>()), fake.calls.map { it.first })
        }
    }

    @Test
    fun `search needs the key and a database`() {
        api(FakeIndex(listOf(suburb))) { assertEquals(HttpStatusCode.Unauthorized, search("ham", withKey = false).status) }
        api(null) { assertEquals(HttpStatusCode.ServiceUnavailable, search("ham").status) }
        api(FakeIndex(failure = IllegalStateException("db down"))) { assertEquals(HttpStatusCode.ServiceUnavailable, search("ham").status) }
    }
}
