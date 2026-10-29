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
        assertEquals("""{"status":"ok","service":"Home Finder API","version":"0.1.0","database":"not_configured"}""", json)
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

    @Test
    fun `an older health response without a database field still decodes`() {
        val old = ApiJson.decodeFromString<HealthResponse>("""{"status":"ok","service":"s","version":"0.1.0"}""")
        assertEquals(HealthResponse.DATABASE_NOT_CONFIGURED, old.database)
    }

    @Test
    fun `tile contract paths and zoom limits are stable`() {
        assertEquals("/tiles/suburbs", TileContract.suburbsPath())
        assertEquals(5, TileContract.SUBURBS_MIN_ZOOM)
        assertEquals(14, TileContract.SUBURBS_MAX_ZOOM)
        assertTrue(TileContract.SUBURBS_MIN_ZOOM < TileContract.SUBURBS_MAX_ZOOM)
        assertEquals(".pbf", TileContract.TILE_SUFFIX)
    }

    @Test
    fun `suburb endpoints and a reference round trip`() {
        assertEquals("/v1/suburbs/at", ApiContract.SUBURBS_AT_PATH)
        assertTrue(ApiContract.SUBURBS_AT_PATH.startsWith(ApiContract.SUBURBS_PATH))
        val ref = SuburbRef("9001", "HAMILTON", "2303")
        assertEquals(ref, ApiJson.decodeFromString<SuburbRef>(ApiJson.encodeToString(ref)))
        val noPostcode = ApiJson.encodeToString(SuburbRef("9002", "LAKE MACQUARIE", null))
        assertTrue(noPostcode.contains("\"postcode\":null"), noPostcode)
    }
}
