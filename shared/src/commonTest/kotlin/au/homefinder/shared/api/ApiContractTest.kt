package au.homefinder.shared.api

import au.homefinder.shared.model.ApiJson
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiContractTest {
    @Test
    fun `health response round trips and is plain JSON`() {
        val health = HealthResponse(HealthResponse.STATUS_OK, "Home Finder API", "0.1.0")
        val json = ApiJson.encodeToString(health)
        assertEquals("""{"status":"ok","service":"Home Finder API","version":"0.1.0"}""", json)
        assertEquals(health, ApiJson.decodeFromString<HealthResponse>(json))
    }

    @Test
    fun `status and error responses round trip`() {
        assertEquals(StatusResponse(true), ApiJson.decodeFromString<StatusResponse>(ApiJson.encodeToString(StatusResponse(true))))
        val error = ApiJson.encodeToString(ApiError(ApiError.UNAUTHORIZED))
        assertEquals("""{"error":"unauthorized"}""", error)
    }

    @Test
    fun `paths and header are stable`() {
        assertEquals("/health", ApiContract.HEALTH_PATH)
        assertTrue(ApiContract.STATUS_PATH.startsWith("/v1/"))
        assertEquals("X-API-Key", ApiContract.API_KEY_HEADER)
    }
}
