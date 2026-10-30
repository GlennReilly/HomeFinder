package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.api.SuburbRef
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.SuburbReport
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SuburbRoutesTest {
    private val key = "0123456789abcdef"
    private val config = ServerConfig("0.0.0.0", 8080, key)
    private val hamilton = SuburbRecord("9001", "HAMILTON", "2303", 3.512)

    private class FakeDirectory(
        private val atResult: SuburbRecord? = null,
        private val byId: Map<String, SuburbRecord> = emptyMap(),
        private val failure: Exception? = null,
        private val stats: Map<String, SuburbStats> = emptyMap(),
        private val amenities: Map<String, SuburbAmenity> = emptyMap(),
        private val schools: Map<String, SuburbSchoolData> = emptyMap(),
    ) : SuburbDirectory {
        val atCalls = mutableListOf<Pair<Double, Double>>()
        override suspend fun findAt(latitude: Double, longitude: Double): SuburbRecord? {
            atCalls += latitude to longitude
            failure?.let { throw it }
            return atResult
        }
        override suspend fun find(suburbId: String): SuburbRecord? {
            failure?.let { throw it }
            return byId[suburbId]
        }
        override suspend fun stats(suburbId: String): SuburbStats? = stats[suburbId]
        override suspend fun amenity(suburbId: String): SuburbAmenity? = amenities[suburbId]
        override suspend fun schools(suburbId: String): SuburbSchoolData? = schools[suburbId]
    }

    private fun api(directory: SuburbDirectory?, block: suspend HttpClient.() -> Unit) = testApplication {
        application { module(config, suburbs = directory) }
        client.block()
    }

    private suspend fun HttpClient.get(path: String, withKey: Boolean = true): HttpResponse =
        get(path) { if (withKey) header(ApiContract.API_KEY_HEADER, key) }

    private suspend fun HttpResponse.error() = ApiJson.decodeFromString<ApiError>(bodyAsText())

    private val at = ApiContract.SUBURBS_AT_PATH

    @Test
    fun `a point inside a suburb returns its reference`() = FakeDirectory(atResult = hamilton).let { fake ->
        api(fake) {
            val response = get("$at?lat=-32.92&lon=151.75")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(SuburbRef("9001", "HAMILTON", "2303"), ApiJson.decodeFromString<SuburbRef>(response.bodyAsText()))
            assertEquals(listOf(-32.92 to 151.75), fake.atCalls)
        }
    }

    @Test
    fun `a point in no suburb is not found`() = api(FakeDirectory()) {
        val response = get("$at?lat=-33.87&lon=151.21")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ApiError(ApiError.NOT_FOUND), response.error())
    }

    @Test
    fun `missing, malformed or out of range coordinates are a bad request`() = FakeDirectory(atResult = hamilton).let { fake ->
        api(fake) {
            listOf("", "?lat=-32.9", "?lon=151.7", "?lat=abc&lon=151.7", "?lat=-32.9&lon=", "?lat=91&lon=151.7", "?lat=-32.9&lon=181", "?lat=NaN&lon=151.7", "?lat=-32.9&lon=Infinity")
                .forEach { query ->
                    val response = get(at + query)
                    assertEquals(HttpStatusCode.BadRequest, response.status, query)
                    assertEquals(ApiError(ApiError.INVALID_COORDINATES), response.error(), query)
                }
            assertTrue(fake.atCalls.isEmpty())
        }
    }

    @Test
    fun `the extreme valid coordinates are accepted`() = api(FakeDirectory()) {
        assertEquals(HttpStatusCode.NotFound, get("$at?lat=90&lon=180").status)
        assertEquals(HttpStatusCode.NotFound, get("$at?lat=-90&lon=-180").status)
    }

    @Test
    fun `suburb endpoints need the API key`() = api(FakeDirectory(atResult = hamilton, byId = mapOf("9001" to hamilton))) {
        assertEquals(HttpStatusCode.Unauthorized, get("$at?lat=-32.92&lon=151.75", withKey = false).status)
        assertEquals(HttpStatusCode.Unauthorized, get("${ApiContract.SUBURBS_PATH}/9001", withKey = false).status)
        assertEquals(HttpStatusCode.Unauthorized, get("$at?lat=-32.92&lon=151.75&key=$key", withKey = false).status)
    }

    @Test
    fun `a suburb report is returned with unknown statistics`() = api(FakeDirectory(byId = mapOf("9001" to hamilton))) {
        val response = get("${ApiContract.SUBURBS_PATH}/9001")
        assertEquals(HttpStatusCode.OK, response.status)
        val report = ApiJson.decodeFromString<SuburbReport>(response.bodyAsText())
        assertEquals("HAMILTON", report.name)
        assertEquals(3.512, report.areaKm2.value)
        assertTrue(report.medianHousePriceAud.isUnknown)
    }

    @Test
    fun `an unknown suburb id is not found`() = api(FakeDirectory(byId = mapOf("9001" to hamilton))) {
        val response = get("${ApiContract.SUBURBS_PATH}/424242")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ApiError(ApiError.NOT_FOUND), response.error())
    }

    @Test
    fun `the at path is not mistaken for a suburb id`() = api(FakeDirectory(atResult = hamilton, byId = mapOf("at" to hamilton))) {
        assertEquals(HttpStatusCode.BadRequest, get(at).status)
    }

    @Test
    fun `without a database the suburb endpoints are unavailable`() = api(null) {
        assertEquals(HttpStatusCode.ServiceUnavailable, get("$at?lat=-32.92&lon=151.75").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, get("${ApiContract.SUBURBS_PATH}/9001").status)
    }

    @Test
    fun `a failing database is unavailable and leaks nothing`() = api(FakeDirectory(failure = IllegalStateException("password=secret"), byId = mapOf("9001" to hamilton))) {
        listOf("$at?lat=-32.92&lon=151.75", "${ApiContract.SUBURBS_PATH}/9001").forEach { path ->
            val response = get(path)
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status, path)
            assertTrue(!response.bodyAsText().contains("secret"))
        }
    }

    @Test
    fun `a suburb id is looked up exactly as given`() = api(FakeDirectory(byId = mapOf("9001" to hamilton))) {
        assertNotNull(get("${ApiContract.SUBURBS_PATH}/9001").takeIf { it.status == HttpStatusCode.OK })
        assertEquals(HttpStatusCode.NotFound, get("${ApiContract.SUBURBS_PATH}/9001%27%20OR%201=1").status)
    }

    @Test
    fun `a report carries the suburb's statistics when they exist`() {
        val stats = SuburbStats(
            LocalDate(2026, 6, 1), 60, 1_050_000, 20, 640_000, 40, 4.2, 300, 560.0, 320.0, 810.0,
        )
        api(FakeDirectory(byId = mapOf("9001" to hamilton), stats = mapOf("9001" to stats))) {
            val report = ApiJson.decodeFromString<SuburbReport>(get("${ApiContract.SUBURBS_PATH}/9001").bodyAsText())
            assertEquals(1_050_000L, report.medianHousePriceAud.value)
            assertEquals(SuburbReportFactory.SALES_SOURCE, report.medianHousePriceAud.source)
            assertEquals(80, report.salesCount.value)
        }
    }

    @Test
    fun `a report without statistics still answers, with the sales marked not loaded`() {
        api(FakeDirectory(byId = mapOf("9001" to hamilton))) {
            val report = ApiJson.decodeFromString<SuburbReport>(get("${ApiContract.SUBURBS_PATH}/9001").bodyAsText())
            assertEquals(SuburbReportFactory.SALES_NOT_LOADED_NOTE, report.medianHousePriceAud.note)
        }
    }

    @Test
    fun `a report carries the suburb's OpenStreetMap roll-up when it exists`() {
        val amenity = SuburbAmenity(1794.0, 860.0, "Ocean Beach", 4_000_000.0, 50_000.0, 1, 3, LocalDate(2026, 10, 29))
        api(FakeDirectory(byId = mapOf("9001" to hamilton), amenities = mapOf("9001" to amenity))) {
            val report = ApiJson.decodeFromString<SuburbReport>(get("${ApiContract.SUBURBS_PATH}/9001").bodyAsText())
            assertEquals(1794, report.beach.value?.fromCentreM)
            assertEquals(860, report.beach.value?.fromNearestEdgeM)
            assertEquals(3, report.shopping.value?.supermarkets)
            assertEquals(0.0125, report.parkAreaShare.value)
        }
        api(FakeDirectory(byId = mapOf("9001" to hamilton))) {
            val report = ApiJson.decodeFromString<SuburbReport>(get("${ApiContract.SUBURBS_PATH}/9001").bodyAsText())
            assertEquals(LotReportFactory.OSM_NOT_LOADED_NOTE, report.beach.note)
        }
    }

    @Test
    fun `a report lists the suburb's public schools with their ICSEA when school data is loaded`() {
        val data = SuburbSchoolData(listOf(SchoolRecord(1, "Hamilton Public School", "Primary School", 1051, 412, "https://hamilton-p.schools.nsw.gov.au")), LocalDate(2026, 10, 2))
        api(FakeDirectory(byId = mapOf("9001" to hamilton), schools = mapOf("9001" to data))) {
            val report = ApiJson.decodeFromString<SuburbReport>(get("${ApiContract.SUBURBS_PATH}/9001").bodyAsText())
            val schools = assertNotNull(report.schools.value)
            assertEquals("Hamilton Public School", schools.publicSchoolsInside.single().name)
            assertEquals(1051, schools.averageIcsea)
            assertEquals("https://hamilton-p.schools.nsw.gov.au", schools.publicSchoolsInside.single().website)
        }
        api(FakeDirectory(byId = mapOf("9001" to hamilton))) {
            val report = ApiJson.decodeFromString<SuburbReport>(get("${ApiContract.SUBURBS_PATH}/9001").bodyAsText())
            assertEquals(SuburbReportFactory.SCHOOLS_NOT_LOADED_NOTE, report.schools.note)
        }
    }
}
