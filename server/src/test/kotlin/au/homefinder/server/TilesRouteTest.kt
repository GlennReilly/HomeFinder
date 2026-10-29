package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.api.HealthResponse
import au.homefinder.shared.api.TileContract
import au.homefinder.shared.model.ApiJson
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

class TilesRouteTest {
    private val key = "0123456789abcdef"
    private val config = ServerConfig("0.0.0.0", 8080, key)
    private val tileBytes = byteArrayOf(26, 5, 10, 3, 1, 2, 3)

    private class FakeTiles(var bytes: ByteArray, var healthy: Boolean = true, var failure: Exception? = null) : SuburbTileSource {
        val requests = mutableListOf<Triple<Int, Int, Int>>()
        override suspend fun tile(zoom: Int, x: Int, y: Int): ByteArray {
            requests += Triple(zoom, x, y)
            failure?.let { throw it }
            return bytes
        }
        override suspend fun isHealthy() = healthy
    }

    private fun tilesApi(tiles: SuburbTileSource?, block: suspend HttpClient.() -> Unit) = testApplication {
        application { module(config, tiles) }
        client.block()
    }

    private suspend fun HttpClient.tile(path: String, withKey: Boolean = true): HttpResponse =
        get(path) { if (withKey) header(ApiContract.API_KEY_HEADER, key) }

    private suspend fun HttpResponse.error() = ApiJson.decodeFromString<ApiError>(bodyAsText())

    private val hamilton = "${TileContract.suburbsPath()}/10/943/611.pbf"

    @Test
    fun `a tile is returned as a vector tile with a cache header`() = FakeTiles(tileBytes).let { fake ->
        tilesApi(fake) {
            val response = tile(hamilton)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(TileContract.MIME_TYPE, response.headers[HttpHeaders.ContentType])
            assertEquals("private, max-age=300", response.headers[HttpHeaders.CacheControl])
            assertContentEquals(tileBytes, response.bodyAsBytes())
            assertEquals(listOf(Triple(10, 943, 611)), fake.requests)
        }
    }

    @Test
    fun `tiles need the API key`() = tilesApi(FakeTiles(tileBytes)) {
        assertEquals(HttpStatusCode.Unauthorized, tile(hamilton, withKey = false).status)
        assertEquals(HttpStatusCode.Unauthorized, get(hamilton) { header(ApiContract.API_KEY_HEADER, "wrong") }.status)
    }

    @Test
    fun `the key may be given as a query parameter for clients that cannot set headers`() = tilesApi(FakeTiles(tileBytes)) {
        assertEquals(HttpStatusCode.OK, get("$hamilton?key=$key").status)
        assertEquals(HttpStatusCode.Unauthorized, get("$hamilton?key=wrong").status)
        assertEquals(HttpStatusCode.Unauthorized, get("$hamilton?key=").status)
    }

    @Test
    fun `a wrong header is not rescued by a right query parameter`() = tilesApi(FakeTiles(tileBytes)) {
        val response = get("$hamilton?key=$key") { header(ApiContract.API_KEY_HEADER, "wrong") }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `the query parameter key is accepted only on tiles`() = tilesApi(FakeTiles(tileBytes)) {
        assertEquals(HttpStatusCode.Unauthorized, get("${ApiContract.STATUS_PATH}?key=$key").status)
    }

    @Test
    fun `zooms outside the served range are empty without asking the source`() = FakeTiles(tileBytes).let { fake ->
        tilesApi(fake) {
            assertEquals(HttpStatusCode.NoContent, tile("${TileContract.suburbsPath()}/4/14/9.pbf").status)
            assertEquals(HttpStatusCode.NoContent, tile("${TileContract.suburbsPath()}/15/30000/20000.pbf").status)
            assertTrue(fake.requests.isEmpty())
            assertEquals(HttpStatusCode.OK, tile("${TileContract.suburbsPath()}/5/29/18.pbf").status)
            assertEquals(HttpStatusCode.OK, tile("${TileContract.suburbsPath()}/14/15088/9788.pbf").status)
        }
    }

    @Test
    fun `an empty tile is no content`() = tilesApi(FakeTiles(ByteArray(0))) {
        assertEquals(HttpStatusCode.NoContent, tile(hamilton).status)
    }

    @Test
    fun `malformed tile coordinates are a bad request`() = FakeTiles(tileBytes).let { fake ->
        tilesApi(fake) {
            listOf("10/943/611", "10/943/611.png", "10/abc/611.pbf", "x/943/611.pbf", "10/-1/611.pbf", "10/1024/611.pbf", "10/943/611.0.pbf")
                .forEach { path ->
                    val response = tile("${TileContract.suburbsPath()}/$path")
                    assertEquals(HttpStatusCode.BadRequest, response.status, path)
                    assertEquals(ApiError(ApiError.INVALID_TILE), response.error(), path)
                }
            assertTrue(fake.requests.isEmpty())
        }
    }

    @Test
    fun `an unknown layer is not found`() = tilesApi(FakeTiles(tileBytes)) {
        val response = tile("${TileContract.TILES_PREFIX}/lots/10/943/611.pbf")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ApiError(ApiError.UNKNOWN_LAYER), response.error())
    }

    @Test
    fun `without a database tiles are unavailable`() = tilesApi(null) {
        val response = tile(hamilton)
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals(ApiError(ApiError.UNAVAILABLE), response.error())
    }

    @Test
    fun `a failing source is unavailable and leaks nothing`() = tilesApi(FakeTiles(tileBytes, failure = IllegalStateException("password=secret db down"))) {
        val response = tile(hamilton)
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(!response.bodyAsText().contains("secret"))
    }

    @Test
    fun `health reports the database state`() {
        suspend fun HttpClient.database() = ApiJson.decodeFromString<HealthResponse>(get(ApiContract.HEALTH_PATH).bodyAsText()).database
        tilesApi(null) { assertEquals("not_configured", database()) }
        tilesApi(FakeTiles(tileBytes, healthy = true)) { assertEquals("up", database()) }
        tilesApi(FakeTiles(tileBytes, healthy = false)) { assertEquals("down", database()) }
    }
}
