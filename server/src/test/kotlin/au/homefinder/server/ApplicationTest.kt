package au.homefinder.server

import au.homefinder.shared.AppInfo
import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.api.HealthResponse
import au.homefinder.shared.api.StatusResponse
import au.homefinder.shared.model.ApiJson
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    private val key = "0123456789abcdef"
    private val config = ServerConfig("0.0.0.0", 8080, key)

    private fun api(block: suspend io.ktor.client.HttpClient.() -> Unit) = testApplication {
        application { module(config) }
        client.block()
    }

    @Test
    fun `health is open and reports the service and version as JSON`() = api {
        val response = get(ApiContract.HEALTH_PATH)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val health = ApiJson.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals(HealthResponse("ok", "Home Finder API", AppInfo.VERSION), health)
    }

    @Test
    fun `health ignores a wrong key`() = api {
        val response = get(ApiContract.HEALTH_PATH) { header(ApiContract.API_KEY_HEADER, "wrong") }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `status without a key is unauthorized with a JSON error`() = api {
        val response = get(ApiContract.STATUS_PATH)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ApiError(ApiError.UNAUTHORIZED), ApiJson.decodeFromString<ApiError>(response.bodyAsText()))
    }

    @Test
    fun `status with a wrong or empty key is unauthorized`() = api {
        listOf("wrong", "", key.dropLast(1), "$key ").forEach { bad ->
            val response = get(ApiContract.STATUS_PATH) { header(ApiContract.API_KEY_HEADER, bad) }
            assertEquals(HttpStatusCode.Unauthorized, response.status, "key '$bad'")
        }
    }

    @Test
    fun `status with the right key proves authentication`() = api {
        val response = get(ApiContract.STATUS_PATH) { header(ApiContract.API_KEY_HEADER, key) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(StatusResponse(true), ApiJson.decodeFromString<StatusResponse>(response.bodyAsText()))
    }

    @Test
    fun `the key header name is case insensitive`() = api {
        val response = get(ApiContract.STATUS_PATH) { header("x-api-key", key) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `an unknown path is not found`() = api {
        assertEquals(HttpStatusCode.NotFound, get("/nothing").status)
        assertEquals(HttpStatusCode.NotFound, get("/v1/nothing") { header(ApiContract.API_KEY_HEADER, key) }.status)
    }

    @Test
    fun `an error response never contains the key`() = api {
        val body = get(ApiContract.STATUS_PATH) { header(ApiContract.API_KEY_HEADER, "wrong") }.bodyAsText()
        assertTrue(!body.contains(key))
    }
}
