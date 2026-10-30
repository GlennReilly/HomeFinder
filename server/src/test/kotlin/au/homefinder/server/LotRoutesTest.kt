package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.api.TileContract
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.LotRef
import au.homefinder.shared.model.LotReport
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LotRoutesTest {
    private val key = "0123456789abcdef"
    private val config = ServerConfig("127.0.0.1", 8080, key)
    private val lot = LotRecord("1001", "1//DP100", "1/100", "9001", "HAMILTON", "2303", 561.4, listOf("12 MAIN STREET, HAMILTON NSW 2303"))
    private val tileBytes = byteArrayOf(26, 5, 10, 3, 1, 2, 3)

    private class FakeLots(
        private val atResult: LotRecord? = null,
        private val byId: Map<String, LotRecord> = emptyMap(),
        private val failure: Exception? = null,
    ) : LotDirectory {
        val atCalls = mutableListOf<Pair<Double, Double>>()
        override suspend fun findAt(latitude: Double, longitude: Double): LotRecord? {
            atCalls += latitude to longitude
            failure?.let { throw it }
            return atResult
        }
        override suspend fun find(lotId: String): LotRecord? {
            failure?.let { throw it }
            return byId[lotId]
        }
    }

    private class FakeLotTiles(var bytes: ByteArray) : LotTileSource {
        val requests = mutableListOf<Triple<Int, Int, Int>>()
        override suspend fun tile(zoom: Int, x: Int, y: Int): ByteArray = bytes.also { requests += Triple(zoom, x, y) }
    }

    private fun api(lots: LotDirectory? = null, tiles: LotTileSource? = null, block: suspend HttpClient.() -> Unit) = testApplication {
        application { module(config, lots = lots, lotTiles = tiles) }
        client.block()
    }

    private suspend fun HttpClient.get(path: String, withKey: Boolean = true): HttpResponse =
        get(path) { if (withKey) header(ApiContract.API_KEY_HEADER, key) }

    private val at = ApiContract.LOTS_AT_PATH

    @Test
    fun `a point inside a lot returns its reference`() = FakeLots(atResult = lot).let { fake ->
        api(fake) {
            val response = get("$at?lat=-32.92&lon=151.75")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(LotRef("1001", "9001"), ApiJson.decodeFromString<LotRef>(response.bodyAsText()))
            assertEquals(listOf(-32.92 to 151.75), fake.atCalls)
        }
    }

    @Test
    fun `a point in no lot is not found`() = api(FakeLots()) {
        val response = get("$at?lat=-33.87&lon=151.21")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ApiError(ApiError.NOT_FOUND), ApiJson.decodeFromString<ApiError>(response.bodyAsText()))
    }

    @Test
    fun `malformed or out of range coordinates are a bad request`() = api(FakeLots(atResult = lot)) {
        listOf("", "?lat=-32.9", "?lat=abc&lon=151.7", "?lat=91&lon=151.7", "?lat=-32.9&lon=181", "?lat=NaN&lon=151.7").forEach {
            assertEquals(HttpStatusCode.BadRequest, get("$at$it").status, it)
        }
    }

    @Test
    fun `a lot report carries the lot, its addresses and measured area`() = api(FakeLots(byId = mapOf("1001" to lot))) {
        val response = get("${ApiContract.LOTS_PATH}/1001")
        assertEquals(HttpStatusCode.OK, response.status)
        val report = ApiJson.decodeFromString<LotReport>(response.bodyAsText())
        assertEquals("HAMILTON", report.suburbName)
        assertEquals(561.4, report.blockAreaM2.value)
        assertEquals(listOf("12 MAIN STREET, HAMILTON NSW 2303"), report.addresses)
        assertEquals(HttpStatusCode.NotFound, get("${ApiContract.LOTS_PATH}/424242").status)
    }

    @Test
    fun `lot lookups need the key and a database`() {
        api(FakeLots(atResult = lot)) {
            assertEquals(HttpStatusCode.Unauthorized, get("$at?lat=-32.9&lon=151.7", withKey = false).status)
            assertEquals(HttpStatusCode.Unauthorized, get("${ApiContract.LOTS_PATH}/1001?key=$key", withKey = false).status)
        }
        api(null) { assertEquals(HttpStatusCode.ServiceUnavailable, get("$at?lat=-32.9&lon=151.7").status) }
        api(FakeLots(failure = IllegalStateException("db down"))) {
            assertEquals(HttpStatusCode.ServiceUnavailable, get("${ApiContract.LOTS_PATH}/1001").status)
        }
    }

    private val streetTile = "${TileContract.lotsPath()}/16/60296/39263.pbf"

    @Test
    fun `lot tiles are served from zoom 16 to 18 and are empty outside`() = FakeLotTiles(tileBytes).let { fake ->
        api(tiles = fake) {
            val response = get(streetTile)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(TileContract.MIME_TYPE, response.headers[HttpHeaders.ContentType])
            assertContentEquals(tileBytes, response.bodyAsBytes())
            assertEquals(HttpStatusCode.OK, get("${TileContract.lotsPath()}/18/241184/157052.pbf").status)
            assertEquals(2, fake.requests.size)
            assertEquals(HttpStatusCode.NoContent, get("${TileContract.lotsPath()}/15/30148/19631.pbf").status)
            assertEquals(HttpStatusCode.NoContent, get("${TileContract.lotsPath()}/19/482368/314104.pbf").status)
            assertEquals(2, fake.requests.size)
        }
    }

    @Test
    fun `lot tiles need the key and a database`() {
        api(tiles = FakeLotTiles(tileBytes)) {
            assertEquals(HttpStatusCode.Unauthorized, get(streetTile, withKey = false).status)
            assertEquals(HttpStatusCode.OK, get("$streetTile?key=$key", withKey = false).status)
        }
        api { assertEquals(HttpStatusCode.ServiceUnavailable, get(streetTile).status) }
    }

    @Test
    fun `an unknown tile layer is still not found`() = api(tiles = FakeLotTiles(tileBytes)) {
        assertEquals(HttpStatusCode.NotFound, get("${TileContract.TILES_PREFIX}/parcels/16/60296/39263.pbf").status)
        assertTrue(TileContract.lotsPath().endsWith("/lots"))
    }
}
