package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.SuburbExportRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportRoutesTest {
    private val key = "0123456789abcdef"
    private val config = ServerConfig("127.0.0.1", 8080, key)
    private val day = LocalDate(2026, 11, 9)
    private val suburbs = mapOf(
        "9001" to SuburbRecord("9001", "HAMILTON", "2303", 3.5),
        "9002" to SuburbRecord("9002", "MAYFIELD", "2304", 4.1),
    )

    private class FakeDirectory(private val records: Map<String, SuburbRecord>, private val failure: Exception? = null) : SuburbDirectory {
        val asked = mutableListOf<String>()
        override suspend fun findAt(latitude: Double, longitude: Double): SuburbRecord? = null
        override suspend fun find(suburbId: String): SuburbRecord? {
            failure?.let { throw it }
            asked += suburbId
            return records[suburbId]
        }
    }

    private fun api(directory: SuburbDirectory?, block: suspend HttpClient.() -> Unit) = testApplication {
        application { module(config, suburbs = directory, today = { day }) }
        client.block()
    }

    private suspend fun HttpClient.export(body: String, withKey: Boolean = true): HttpResponse = post(ApiContract.SUBURBS_EXPORT_PDF_PATH) {
        if (withKey) header(ApiContract.API_KEY_HEADER, key)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun json(request: SuburbExportRequest) = ApiJson.encodeToString(request)

    private suspend fun HttpResponse.error() = ApiJson.decodeFromString<ApiError>(bodyAsText())

    @Test
    fun `the matching suburbs come back as a PDF download named for the day`() = FakeDirectory(suburbs).let { directory ->
        api(directory) {
            val response = export(json(SuburbExportRequest(listOf("9002", "9001"), listOf("Homes rented: At most 45%"))))
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.headers[HttpHeaders.ContentType]?.let(ContentType::parse)?.withoutParameters())
            assertEquals("attachment; filename=homefinder-suburbs-2026-11-09.pdf", response.headers[HttpHeaders.ContentDisposition])
            assertEquals(EXPORT_CACHE_CONTROL, response.headers[HttpHeaders.CacheControl])
            val bytes = response.bodyAsBytes()
            assertEquals("%PDF", bytes.copyOfRange(0, 4).decodeToString())
            val text = Loader.loadPDF(bytes).use { PDFTextStripper().getText(it) }
            assertTrue("Homes rented: At most 45%" in text && "Made on 2026-11-09" in text, text)
            // In the order asked for (the page sorts them by name).
            assertTrue(text.indexOf("MAYFIELD\nPostcode") in 0 until text.indexOf("HAMILTON\nPostcode"), text)
            assertEquals(listOf("9002", "9001"), directory.asked)
        }
    }

    @Test
    fun `suburbs not found are left out and counted, and with none found it is not found`() = api(FakeDirectory(suburbs)) {
        val some = export(json(SuburbExportRequest(listOf("9001", "nope"))))
        assertEquals(HttpStatusCode.OK, some.status)
        val text = Loader.loadPDF(some.bodyAsBytes()).use { PDFTextStripper().getText(it) }
        assertTrue("1 of the suburbs asked for could not be found" in text, text)
        val none = export(json(SuburbExportRequest(listOf("nope"))))
        assertEquals(HttpStatusCode.NotFound, none.status)
        assertEquals(ApiError(ApiError.NOT_FOUND), none.error())
    }

    @Test
    fun `an empty list, a body that is not a request, or one over the cap is a bad request`() = api(FakeDirectory(suburbs)) {
        assertEquals(ApiError(ApiError.INVALID_EXPORT), export(json(SuburbExportRequest(emptyList()))).also { assertEquals(HttpStatusCode.BadRequest, it.status) }.error())
        assertEquals(ApiError(ApiError.INVALID_EXPORT), export(json(SuburbExportRequest(listOf(" ", "")))).error())
        assertEquals(HttpStatusCode.BadRequest, export("not json").status)
        assertEquals(HttpStatusCode.BadRequest, export("""{"filters":[]}""").status)
        val tooMany = export(json(SuburbExportRequest((0..ApiContract.MAX_EXPORT_SUBURBS).map { "id$it" })))
        assertEquals(HttpStatusCode.BadRequest, tooMany.status)
        assertEquals(ApiError(ApiError.TOO_MANY_SUBURBS), tooMany.error())
    }

    @Test
    fun `without the API key the export is refused`() = FakeDirectory(suburbs).let { directory ->
        api(directory) {
            val response = export(json(SuburbExportRequest(listOf("9001"))), withKey = false)
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(ApiError(ApiError.UNAUTHORIZED), response.error())
            assertEquals(emptyList(), directory.asked)
        }
    }

    @Test
    fun `without a database, or when a lookup fails, it is unavailable`() {
        api(null) { assertEquals(HttpStatusCode.ServiceUnavailable, export(json(SuburbExportRequest(listOf("9001")))).status) }
        api(FakeDirectory(suburbs, IllegalStateException("db down"))) {
            val response = export(json(SuburbExportRequest(listOf("9001"))))
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals(ApiError(ApiError.UNAVAILABLE), response.error())
        }
    }
}
