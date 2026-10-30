package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.HomelyRating
import au.homefinder.shared.model.SuburbRatingPoint
import au.homefinder.shared.model.SuburbRatingsResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RatingRoutesTest {
    private val key = "0123456789abcdef"
    private val config = ServerConfig("127.0.0.1", 8080, key)
    private val hamilton = SuburbRecord("9001", "HAMILTON", "2303", 3.5)
    private val day = LocalDate(2026, 10, 30)

    private class FakeStore(val failure: Exception? = null) : RatingStore {
        val rows = linkedMapOf<String, StoredRating>()
        override suspend fun get(suburbId: String): StoredRating? { failure?.let { throw it }; return rows[suburbId] }
        override suspend fun put(suburbId: String, rating: HomelyRating, ratedOn: LocalDate) { failure?.let { throw it }; rows[suburbId] = StoredRating(rating, ratedOn) }
        override suspend fun delete(suburbId: String) { failure?.let { throw it }; rows.remove(suburbId) }
        override suspend fun all(): List<SuburbRatingPoint> { failure?.let { throw it }; return rows.map { (id, r) -> SuburbRatingPoint(id, "HAMILTON", r.rating, -32.92, 151.75) } }
    }

    private class FakeDirectory(private val known: Map<String, SuburbRecord>) : SuburbDirectory {
        override suspend fun findAt(latitude: Double, longitude: Double): SuburbRecord? = null
        override suspend fun find(suburbId: String): SuburbRecord? = known[suburbId]
    }

    private fun api(store: RatingStore?, block: suspend HttpClient.() -> Unit) = testApplication {
        application { module(config, suburbs = FakeDirectory(mapOf("9001" to hamilton)), ratings = store, today = { day }) }
        client.block()
    }

    private suspend fun HttpClient.put(path: String, body: String, withKey: Boolean = true): HttpResponse = put(path) {
        if (withKey) header(ApiContract.API_KEY_HEADER, key)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpClient.keyed(path: String, delete: Boolean = false): HttpResponse =
        (if (delete) delete(path) { header(ApiContract.API_KEY_HEADER, key) } else get(path) { header(ApiContract.API_KEY_HEADER, key) })

    private val path = ApiContract.suburbRatingPath("9001")

    @Test
    fun `a rating is saved with today's date and returned`() = FakeStore().let { store ->
        api(store) {
            val response = put(path, """{"stars":2.1,"reviewCount":7}""")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(HomelyRating(2.1, 7), ApiJson.decodeFromString<HomelyRating>(response.bodyAsText()))
            assertEquals(StoredRating(HomelyRating(2.1, 7), day), store.rows["9001"])
        }
    }

    @Test
    fun `stars are rounded to one decimal place and the review count is optional`() = FakeStore().let { store ->
        api(store) {
            put(path, """{"stars":2.14}""")
            assertEquals(HomelyRating(2.1, null), store.rows["9001"]?.rating)
            put(path, """{"stars":4.95,"reviewCount":0}""")
            assertEquals(HomelyRating(5.0, 0), store.rows["9001"]?.rating)
            put(path, """{"stars":0}""")
            assertEquals(HomelyRating(0.0, null), store.rows["9001"]?.rating)
        }
    }

    @Test
    fun `saving again replaces the rating`() = FakeStore().let { store ->
        api(store) {
            put(path, """{"stars":2.1,"reviewCount":7}""")
            put(path, """{"stars":3.4,"reviewCount":9}""")
            assertEquals(listOf(HomelyRating(3.4, 9)), store.rows.values.map { it.rating })
        }
    }

    @Test
    fun `a rating out of range, not a number or malformed is a bad request and saves nothing`() = FakeStore().let { store ->
        api(store) {
            listOf(
                """{"stars":5.1}""", """{"stars":-0.1}""", """{"stars":"abc"}""", """{"reviewCount":3}""", """{"stars":3,"reviewCount":-1}""",
                """{"stars":3,"reviewCount":2000000}""", """not json""", "", """[]""", """{"stars":1e999}""",
            ).forEach {
                val response = put(path, it)
                assertEquals(HttpStatusCode.BadRequest, response.status, it)
                assertEquals(ApiError(ApiError.INVALID_RATING), ApiJson.decodeFromString<ApiError>(response.bodyAsText()), it)
            }
            assertEquals(emptyMap(), store.rows)
        }
    }

    @Test
    fun `an unknown suburb cannot be rated`() = FakeStore().let { store ->
        api(store) {
            assertEquals(HttpStatusCode.NotFound, put(ApiContract.suburbRatingPath("424242"), """{"stars":3}""").status)
            assertEquals(emptyMap(), store.rows)
        }
    }

    @Test
    fun `a rating can be removed, and removing a missing one is fine`() = FakeStore().let { store ->
        api(store) {
            put(path, """{"stars":2.1}""")
            assertEquals(HttpStatusCode.NoContent, keyed(path, delete = true).status)
            assertNull(store.rows["9001"])
            assertEquals(HttpStatusCode.NoContent, keyed(path, delete = true).status)
        }
    }

    @Test
    fun `the list holds every rated suburb`() = FakeStore().let { store ->
        api(store) {
            assertEquals(emptyList(), ApiJson.decodeFromString<SuburbRatingsResponse>(keyed(ApiContract.SUBURB_RATINGS_PATH).bodyAsText()).ratings)
            put(path, """{"stars":2.1,"reviewCount":7}""")
            val list = ApiJson.decodeFromString<SuburbRatingsResponse>(keyed(ApiContract.SUBURB_RATINGS_PATH).bodyAsText()).ratings
            assertEquals(listOf("9001"), list.map { it.suburbId })
            assertEquals(HomelyRating(2.1, 7), list.single().rating)
        }
    }

    @Test
    fun `every rating call needs the key`() = FakeStore().let { store ->
        api(store) {
            assertEquals(HttpStatusCode.Unauthorized, put(path, """{"stars":3}""", withKey = false).status)
            assertEquals(HttpStatusCode.Unauthorized, delete(path).status)
            assertEquals(HttpStatusCode.Unauthorized, get(ApiContract.SUBURB_RATINGS_PATH).status)
            assertEquals(HttpStatusCode.Unauthorized, get("${ApiContract.SUBURB_RATINGS_PATH}?key=$key").status)
            assertEquals(emptyMap(), store.rows)
        }
    }

    @Test
    fun `without a database or when it fails every rating call is unavailable`() {
        api(null) {
            assertEquals(HttpStatusCode.ServiceUnavailable, put(path, """{"stars":3}""").status)
            assertEquals(HttpStatusCode.ServiceUnavailable, keyed(path, delete = true).status)
            assertEquals(HttpStatusCode.ServiceUnavailable, keyed(ApiContract.SUBURB_RATINGS_PATH).status)
        }
        api(FakeStore(IllegalStateException("db down"))) {
            assertEquals(HttpStatusCode.ServiceUnavailable, put(path, """{"stars":3}""").status)
            assertEquals(HttpStatusCode.ServiceUnavailable, keyed(path, delete = true).status)
            assertEquals(HttpStatusCode.ServiceUnavailable, keyed(ApiContract.SUBURB_RATINGS_PATH).status)
        }
    }

    @Test
    fun `the ratings list is not mistaken for a suburb id`() = FakeStore().let { store ->
        api(store) {
            // GET /v1/suburbs/ratings is the list; a suburb called "ratings" could never be reached, which is fine for numeric ids.
            assertEquals(HttpStatusCode.OK, keyed(ApiContract.SUBURB_RATINGS_PATH).status)
        }
    }

    @Test
    fun `the suburb report shows the rating once saved and not entered after removing it`() = FakeStore().let { store ->
        api(store) {
            fun report(text: String) = ApiJson.decodeFromString<au.homefinder.shared.model.SuburbReport>(text)
            assertEquals(true, report(keyed(ApiContract.SUBURBS_PATH + "/9001").bodyAsText()).homelyRating.isUnknown)
            put(path, """{"stars":2.1,"reviewCount":7}""")
            val shown = report(keyed(ApiContract.SUBURBS_PATH + "/9001").bodyAsText()).homelyRating
            assertEquals(HomelyRating(2.1, 7), shown.value)
            assertEquals(day, shown.asOf)
            keyed(path, delete = true)
            assertEquals(true, report(keyed(ApiContract.SUBURBS_PATH + "/9001").bodyAsText()).homelyRating.isUnknown)
        }
    }
}
