package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.NaplanEntry
import au.homefinder.shared.model.NaplanScore
import au.homefinder.shared.model.SchoolNaplan
import au.homefinder.shared.model.SchoolNaplanResponse
import au.homefinder.shared.model.SuburbReport
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

class NaplanRoutesTest {
    private val key = "0123456789abcdef"
    private val config = ServerConfig("127.0.0.1", 8080, key)
    private val day = LocalDate(2026, 11, 8)
    private val hamilton = SuburbRecord("9001", "HAMILTON", "2303", 3.5)
    private val school = SchoolRecord(1234, "Hamilton Public School", "Primary School", 1051, 412, null)
    private val lot = LotRecord("1001", "1//DP100", "1/100", "9001", "HAMILTON", "2303", 561.4, listOf("12 MAIN STREET, HAMILTON NSW 2303"))

    /** Knows schools 1234 and 5678; any other code is not a school. */
    private inner class FakeStore(val failure: Exception? = null) : NaplanStore {
        val rows = linkedMapOf<String, NaplanScore>()
        private val known = setOf("1234", "5678")
        override suspend fun forSchools(codes: Collection<String>): Map<String, NaplanScore> { failure?.let { throw it }; return rows.filterKeys { it in codes } }
        override suspend fun put(schoolCode: String, entry: NaplanEntry): NaplanWrite {
            failure?.let { throw it }
            if (schoolCode !in known) return NaplanWrite.UnknownSchool
            return NaplanWrite.Saved(NaplanScore(entry.score, entry.year, entry.note, day).also { rows[schoolCode] = it })
        }
        override suspend fun delete(schoolCode: String) { failure?.let { throw it }; rows.remove(schoolCode) }
        override suspend fun all(): List<SchoolNaplan> { failure?.let { throw it }; return rows.map { (code, naplan) -> SchoolNaplan(code, naplan) } }
    }

    private inner class FakeSuburbs : SuburbDirectory {
        override suspend fun findAt(latitude: Double, longitude: Double): SuburbRecord? = null
        override suspend fun find(suburbId: String): SuburbRecord? = hamilton.takeIf { suburbId == it.suburbId }
        override suspend fun schools(suburbId: String): SuburbSchoolData = SuburbSchoolData(listOf(school), day)
    }

    private inner class FakeLots : LotDirectory {
        override suspend fun findAt(latitude: Double, longitude: Double): LotRecord? = null
        override suspend fun find(lotId: String): LotRecord? = lot.takeIf { lotId == it.lotId }
        override suspend fun schools(lotId: String): LotSchoolData = LotSchoolData(listOf(NearSchool(school, 600.0)), day)
    }

    private fun api(store: NaplanStore?, block: suspend HttpClient.() -> Unit) = testApplication {
        application { module(config, suburbs = FakeSuburbs(), lots = FakeLots(), naplan = store, today = { day }) }
        client.block()
    }

    private suspend fun HttpClient.put(path: String, body: String, withKey: Boolean = true): HttpResponse = put(path) {
        if (withKey) header(ApiContract.API_KEY_HEADER, key)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpClient.keyed(path: String, delete: Boolean = false): HttpResponse =
        (if (delete) delete(path) { header(ApiContract.API_KEY_HEADER, key) } else get(path) { header(ApiContract.API_KEY_HEADER, key) })

    private val path = ApiContract.schoolNaplanPath("1234")

    @Test
    fun `a figure is saved with the day and returned`() = FakeStore().let { store ->
        api(store) {
            val response = put(path, """{"score":512,"year":2025,"note":"  Year 5 reading "}""")
            assertEquals(HttpStatusCode.OK, response.status)
            val expected = NaplanScore(512, 2025, "Year 5 reading", day)
            assertEquals(expected, ApiJson.decodeFromString<NaplanScore>(response.bodyAsText()))
            assertEquals(expected, store.rows["1234"])
        }
    }

    @Test
    fun `saving again replaces the figure, and the note is optional`() = FakeStore().let { store ->
        api(store) {
            put(path, """{"score":512,"year":2024,"note":"old"}""")
            put(path, """{"score":530,"year":2025}""")
            assertEquals(listOf(NaplanScore(530, 2025, null, day)), store.rows.values.toList())
        }
    }

    @Test
    fun `a figure out of range, not whole numbers or malformed is a bad request and saves nothing`() = FakeStore().let { store ->
        api(store) {
            listOf(
                """{"score":1001,"year":2025}""", """{"score":-1,"year":2025}""", """{"score":512,"year":2007}""", """{"score":512,"year":2027}""",
                """{"score":512.5,"year":2025}""", """{"score":"abc","year":2025}""", """{"year":2025}""", """{"score":512}""",
                """{"score":512,"year":2025,"note":"${"x".repeat(201)}"}""", """not json""", "", """[]""",
            ).forEach {
                val response = put(path, it)
                assertEquals(HttpStatusCode.BadRequest, response.status, it)
                assertEquals(ApiError(ApiError.INVALID_NAPLAN), ApiJson.decodeFromString<ApiError>(response.bodyAsText()), it)
            }
            assertEquals(emptyMap(), store.rows)
        }
    }

    @Test
    fun `an unknown or malformed school code cannot be given a figure`() = FakeStore().let { store ->
        api(store) {
            assertEquals(HttpStatusCode.NotFound, put(ApiContract.schoolNaplanPath("4242"), """{"score":512,"year":2025}""").status)
            assertEquals(HttpStatusCode.NotFound, put(ApiContract.schoolNaplanPath("abc"), """{"score":512,"year":2025}""").status)
            assertEquals(emptyMap(), store.rows)
        }
    }

    @Test
    fun `a figure can be removed, and removing a missing one is fine`() = FakeStore().let { store ->
        api(store) {
            put(path, """{"score":512,"year":2025}""")
            assertEquals(HttpStatusCode.NoContent, keyed(path, delete = true).status)
            assertNull(store.rows["1234"])
            assertEquals(HttpStatusCode.NoContent, keyed(path, delete = true).status)
        }
    }

    @Test
    fun `the list holds every figure, or those for the codes asked for`() = FakeStore().let { store ->
        api(store) {
            fun list(text: String) = ApiJson.decodeFromString<SchoolNaplanResponse>(text).entries
            assertEquals(emptyList(), list(keyed(ApiContract.SCHOOL_NAPLAN_PATH).bodyAsText()))
            put(path, """{"score":512,"year":2025}""")
            put(ApiContract.schoolNaplanPath("5678"), """{"score":600,"year":2024}""")
            assertEquals(listOf("1234", "5678"), list(keyed(ApiContract.SCHOOL_NAPLAN_PATH).bodyAsText()).map { it.schoolCode })
            assertEquals(listOf("5678"), list(keyed("${ApiContract.SCHOOL_NAPLAN_PATH}?codes=5678,9999").bodyAsText()).map { it.schoolCode })
            val tooMany = (1..201).joinToString(",")
            assertEquals(HttpStatusCode.BadRequest, keyed("${ApiContract.SCHOOL_NAPLAN_PATH}?codes=$tooMany").status)
        }
    }

    @Test
    fun `every NAPLAN call needs the key`() = FakeStore().let { store ->
        api(store) {
            assertEquals(HttpStatusCode.Unauthorized, put(path, """{"score":512,"year":2025}""", withKey = false).status)
            assertEquals(HttpStatusCode.Unauthorized, delete(path).status)
            assertEquals(HttpStatusCode.Unauthorized, get(ApiContract.SCHOOL_NAPLAN_PATH).status)
            assertEquals(HttpStatusCode.Unauthorized, get("${ApiContract.SCHOOL_NAPLAN_PATH}?key=$key").status)
            assertEquals(emptyMap(), store.rows)
        }
    }

    @Test
    fun `without a database or when it fails every NAPLAN call is unavailable`() {
        api(null) {
            assertEquals(HttpStatusCode.ServiceUnavailable, put(path, """{"score":512,"year":2025}""").status)
            assertEquals(HttpStatusCode.ServiceUnavailable, keyed(path, delete = true).status)
            assertEquals(HttpStatusCode.ServiceUnavailable, keyed(ApiContract.SCHOOL_NAPLAN_PATH).status)
        }
        api(FakeStore(IllegalStateException("db down"))) {
            assertEquals(HttpStatusCode.ServiceUnavailable, put(path, """{"score":512,"year":2025}""").status)
            assertEquals(HttpStatusCode.ServiceUnavailable, keyed(path, delete = true).status)
            assertEquals(HttpStatusCode.ServiceUnavailable, keyed(ApiContract.SCHOOL_NAPLAN_PATH).status)
        }
    }

    @Test
    fun `the suburb and lot reports carry the figure on the school once saved, and not after removing it`() = FakeStore().let { store ->
        api(store) {
            suspend fun suburbSchool() = ApiJson.decodeFromString<SuburbReport>(keyed(ApiContract.SUBURBS_PATH + "/9001").bodyAsText()).schools.value!!.publicSchoolsInside.single()
            suspend fun lotSchool() = ApiJson.decodeFromString<LotReport>(keyed(ApiContract.LOTS_PATH + "/1001").bodyAsText()).schools.value!!.nearest.single().school
            assertNull(suburbSchool().naplan)
            assertNull(lotSchool().naplan)
            put(path, """{"score":512,"year":2025}""")
            assertEquals(NaplanScore(512, 2025, null, day), suburbSchool().naplan)
            assertEquals(NaplanScore(512, 2025, null, day), lotSchool().naplan)
            assertEquals(1051, suburbSchool().icsea, "the rest of the school is unchanged")
            keyed(path, delete = true)
            assertNull(suburbSchool().naplan)
        }
    }

    @Test
    fun `without a NAPLAN store the reports are served as before`() {
        testApplication {
            application { module(config, suburbs = FakeSuburbs(), lots = FakeLots(), today = { day }) }
            val report = client.get(ApiContract.SUBURBS_PATH + "/9001") { header(ApiContract.API_KEY_HEADER, key) }
            assertEquals(HttpStatusCode.OK, report.status)
            assertNull(ApiJson.decodeFromString<SuburbReport>(report.bodyAsText()).schools.value!!.publicSchoolsInside.single().naplan)
        }
    }
}
