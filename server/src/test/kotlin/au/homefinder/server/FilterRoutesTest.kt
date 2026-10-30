package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.SuburbFilterValue
import au.homefinder.shared.model.SuburbFiltersResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FilterRoutesTest {
    private val key = "0123456789abcdef"
    private val config = ServerConfig("127.0.0.1", 8080, key)
    private val day = LocalDate(2026, 10, 2)

    private class FakeDirectory(private val data: SuburbFilterData?, private val failure: Exception? = null) : SuburbDirectory {
        override suspend fun findAt(latitude: Double, longitude: Double): SuburbRecord? = null
        override suspend fun find(suburbId: String): SuburbRecord? = null
        override suspend fun filterValues(): SuburbFilterData? { failure?.let { throw it }; return data }
    }

    private fun api(directory: SuburbDirectory?, block: suspend HttpClient.() -> Unit) = testApplication {
        application { module(config, suburbs = directory) }
        client.block()
    }

    @Test
    fun `every suburb is listed with its average ICSEA, the source and the extraction day`() = api(
        FakeDirectory(SuburbFilterData(listOf(SuburbFilterRow("9001", 1032), SuburbFilterRow("9002", null)), day)),
    ) {
        val response = get(ApiContract.SUBURB_FILTERS_PATH) { header(ApiContract.API_KEY_HEADER, key) }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = ApiJson.decodeFromString<SuburbFiltersResponse>(response.bodyAsText())
        assertEquals(listOf(SuburbFilterValue("9001", 1032), SuburbFilterValue("9002", null)), body.suburbs)
        assertEquals(day, body.icseaAsOf)
        assertEquals(SuburbReportFactory.SCHOOLS_SOURCE, body.icseaSource)
    }

    @Test
    fun `before the schools are loaded the response is empty with no date, not an error`() = api(FakeDirectory(null)) {
        val response = get(ApiContract.SUBURB_FILTERS_PATH) { header(ApiContract.API_KEY_HEADER, key) }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = ApiJson.decodeFromString<SuburbFiltersResponse>(response.bodyAsText())
        assertEquals(emptyList(), body.suburbs)
        assertNull(body.icseaAsOf)
    }

    @Test
    fun `the key is required`() = api(FakeDirectory(null)) {
        assertEquals(HttpStatusCode.Unauthorized, get(ApiContract.SUBURB_FILTERS_PATH).status)
    }

    @Test
    fun `without a database it is unavailable`() = api(null) {
        val response = get(ApiContract.SUBURB_FILTERS_PATH) { header(ApiContract.API_KEY_HEADER, key) }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(ApiError.UNAVAILABLE, ApiJson.decodeFromString<ApiError>(response.bodyAsText()).error)
    }

    @Test
    fun `a database failure is unavailable`() = api(FakeDirectory(null, IllegalStateException("down"))) {
        val response = get(ApiContract.SUBURB_FILTERS_PATH) { header(ApiContract.API_KEY_HEADER, key) }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }
}
