package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.FilterProvenance
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
        FakeDirectory(SuburbFilterData(listOf(SuburbFilterRow("9001", 1032, name = "Alpha"), SuburbFilterRow("9002", null)), day)),
    ) {
        val response = get(ApiContract.SUBURB_FILTERS_PATH) { header(ApiContract.API_KEY_HEADER, key) }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = ApiJson.decodeFromString<SuburbFiltersResponse>(response.bodyAsText())
        assertEquals(listOf(SuburbFilterValue("9001", 1032, name = "Alpha"), SuburbFilterValue("9002", null)), body.suburbs)
        assertEquals(day, body.icseaAsOf)
        assertEquals(SuburbReportFactory.SCHOOLS_SOURCE, body.icseaSource)
    }

    @Test
    fun `beach, park and rented values come with their own sources and dates, and only for the data sets that are loaded`() = api(
        FakeDirectory(
            SuburbFilterData(
                listOf(SuburbFilterRow("9001", 1032, 3.5, 0.1, 0.45), SuburbFilterRow("9002", null, null, null, null)),
                day, osmLoaded = true, osmAsOf = LocalDate(2026, 10, 29), censusLoaded = true,
            ),
        ),
    ) {
        val body = ApiJson.decodeFromString<SuburbFiltersResponse>(get(ApiContract.SUBURB_FILTERS_PATH) { header(ApiContract.API_KEY_HEADER, key) }.bodyAsText())
        assertEquals(SuburbFilterValue("9001", 1032, 3.5, 0.1, 0.45), body.suburbs.first())
        assertEquals(SuburbFilterValue("9002"), body.suburbs.last())
        assertEquals(FilterProvenance(LotReportFactory.OSM_SOURCE, LocalDate(2026, 10, 29)), body.beach)
        assertEquals(body.beach, body.parks)
        assertEquals(FilterProvenance(SuburbReportFactory.CENSUS_SOURCE, SuburbReportFactory.CENSUS_DATE), body.rented)
    }

    @Test
    fun `the SEIFA decile and its provenance come only when the SEIFA data is loaded`() = api(
        FakeDirectory(SuburbFilterData(listOf(SuburbFilterRow("9001", null, seifaDecile = 8), SuburbFilterRow("9002", null)), day, seifaLoaded = true)),
    ) {
        val body = ApiJson.decodeFromString<SuburbFiltersResponse>(get(ApiContract.SUBURB_FILTERS_PATH) { header(ApiContract.API_KEY_HEADER, key) }.bodyAsText())
        assertEquals(8, body.suburbs.first().seifaDecile)
        assertNull(body.suburbs.last().seifaDecile)
        assertEquals(FilterProvenance(SuburbReportFactory.SEIFA_SOURCE, SuburbReportFactory.CENSUS_DATE), body.seifa)
    }

    @Test
    fun `crime rates and their provenance come only when the crime data is loaded`() = api(
        FakeDirectory(
            SuburbFilterData(
                listOf(SuburbFilterRow("9001", null, violentPer1000 = 4.2, propertyPer1000 = 12.5), SuburbFilterRow("9002", null)),
                day, crimeLoaded = true, crimeAsOf = LocalDate(2026, 6, 30),
            ),
        ),
    ) {
        val body = ApiJson.decodeFromString<SuburbFiltersResponse>(get(ApiContract.SUBURB_FILTERS_PATH) { header(ApiContract.API_KEY_HEADER, key) }.bodyAsText())
        assertEquals(4.2, body.suburbs.first().violentPer1000)
        assertEquals(12.5, body.suburbs.first().propertyPer1000)
        assertNull(body.suburbs.last().violentPer1000)
        assertEquals(FilterProvenance(SuburbReportFactory.CRIME_SOURCE, LocalDate(2026, 6, 30)), body.crime)
    }

    @Test
    fun `the zoned-school ICSEA and its provenance come only when the zones and schools are loaded`() = api(
        FakeDirectory(SuburbFilterData(listOf(SuburbFilterRow("9001", null, zonedIcsea = 1042), SuburbFilterRow("9002", null)), day, zonedLoaded = true, zonedAsOf = day)),
    ) {
        val body = ApiJson.decodeFromString<SuburbFiltersResponse>(get(ApiContract.SUBURB_FILTERS_PATH) { header(ApiContract.API_KEY_HEADER, key) }.bodyAsText())
        assertEquals(1042, body.suburbs.first().zonedIcsea)
        assertNull(body.suburbs.last().zonedIcsea)
        assertEquals(FilterProvenance(SuburbReportFactory.ZONED_FILTER_SOURCE, day), body.zoned)
    }

    @Test
    fun `a data set that is not loaded has no provenance`() = api(FakeDirectory(SuburbFilterData(listOf(SuburbFilterRow("9001", 1032)), day))) {
        val body = ApiJson.decodeFromString<SuburbFiltersResponse>(get(ApiContract.SUBURB_FILTERS_PATH) { header(ApiContract.API_KEY_HEADER, key) }.bodyAsText())
        assertNull(body.beach)
        assertNull(body.parks)
        assertNull(body.rented)
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
