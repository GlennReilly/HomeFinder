package au.homefinder.server

import au.homefinder.shared.AppInfo
import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.api.HealthResponse
import au.homefinder.shared.api.StatusResponse
import au.homefinder.shared.api.TileContract
import au.homefinder.shared.model.ApiJson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import java.io.File

private const val AUTHENTICATED_PREFIX = "/v1"
private const val TILE_CACHE_CONTROL = "private, max-age=300"
private const val WEB_INDEX = "index.html"

/** The web bundle has no content hashes in its file names, so browsers must revalidate it on every load. */
private const val WEB_CACHE_CONTROL = "no-cache"

/**
 * The API: an open health check and, behind the API key, everything else. [suburbTiles] is null when no database is
 * configured; tile requests then answer 503 and the health check reports the database as not configured.
 */
fun Application.module(
    config: ServerConfig,
    suburbTiles: SuburbTileSource? = null,
    suburbs: SuburbDirectory? = null,
    lotTiles: LotTileSource? = null,
    lots: LotDirectory? = null,
    search: SearchIndex? = null,
) {
    install(ContentNegotiation) { json(ApiJson) }
    val tileLayers = mapOf(
        TileContract.SUBURBS_LAYER to TileLayer(
            TileContract.SUBURBS_MIN_ZOOM, TileContract.SUBURBS_MAX_ZOOM, suburbTiles?.let { source -> { z, x, y -> source.tile(z, x, y) } },
        ),
        TileContract.LOTS_LAYER to TileLayer(
            TileContract.LOTS_MIN_ZOOM, TileContract.LOTS_MAX_ZOOM, lotTiles?.let { source -> { z, x, y -> source.tile(z, x, y) } },
        ),
    )
    routing {
        get(ApiContract.HEALTH_PATH) {
            val database = when {
                suburbTiles == null -> HealthResponse.DATABASE_NOT_CONFIGURED
                suburbTiles.isHealthy() -> HealthResponse.DATABASE_UP
                else -> HealthResponse.DATABASE_DOWN
            }
            call.respond(HealthResponse(HealthResponse.STATUS_OK, ServerInfo.name, AppInfo.VERSION, database))
        }
        route(AUTHENTICATED_PREFIX) {
            requireApiKey(config.apiKey)
            get(ApiContract.STATUS_PATH.removePrefix(AUTHENTICATED_PREFIX)) {
                call.respond(StatusResponse(authenticated = true))
            }
            suburbRoutes(suburbs)
            lotRoutes(lots)
            searchRoutes(search)
        }
        route(TileContract.TILES_PREFIX) {
            requireApiKey(config.apiKey, acceptQueryParameter = true)
            get("/{layer}/{z}/{x}/{y}") { respondWithTile(call, tileLayers) }
        }
        // Registered last: the API routes above are more specific, so they win; this only answers what is left.
        config.webRoot?.let { root ->
            staticFiles("/", File(root), index = WEB_INDEX) {
                cacheControl { listOf(io.ktor.http.CacheControl.NoCache(null)) }
            }
        }
    }
}

/** One tile layer: the zooms it is served at and where its tiles come from (null when no database is configured). */
private class TileLayer(val minZoom: Int, val maxZoom: Int, val source: (suspend (Int, Int, Int) -> ByteArray)?)

private suspend fun respondWithTile(call: RoutingCall, layers: Map<String, TileLayer>) {
    val layer = layers[call.parameters["layer"]]
    if (layer == null) {
        call.respond(HttpStatusCode.NotFound, ApiError(ApiError.UNKNOWN_LAYER))
        return
    }
    val zoom = call.parameters["z"]?.toIntOrNull()
    val x = call.parameters["x"]?.toIntOrNull()
    val y = call.parameters["y"]?.takeIf { it.endsWith(TileContract.TILE_SUFFIX) }
        ?.removeSuffix(TileContract.TILE_SUFFIX)?.toIntOrNull()
    if (zoom == null || x == null || y == null || !TileMath.isValid(zoom, x, y)) {
        call.respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_TILE))
        return
    }
    if (zoom !in layer.minZoom..layer.maxZoom) {
        call.respond(HttpStatusCode.NoContent)
        return
    }
    val source = layer.source
    if (source == null) {
        call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        return
    }
    val bytes = try {
        source(zoom, x, y)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        return
    }
    if (bytes.isEmpty()) {
        call.respond(HttpStatusCode.NoContent)
    } else {
        call.response.header(HttpHeaders.CacheControl, TILE_CACHE_CONTROL)
        call.respondBytes(bytes, ContentType.parse(TileContract.MIME_TYPE))
    }
}
