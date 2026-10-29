package au.homefinder.shared.api

import kotlinx.serialization.Serializable

/** The parts of the HTTP API that the server and every client must agree on. */
object ApiContract {
    const val HEALTH_PATH: String = "/health"
    const val STATUS_PATH: String = "/v1/status"
    const val API_KEY_HEADER: String = "X-API-Key"
    const val DEFAULT_PORT: Int = 8080
}

/** Vector tile endpoints: GET /tiles/{layer}/{z}/{x}/{y}.pbf, zoom limits and layer names shared with the clients. */
object TileContract {
    const val TILES_PREFIX: String = "/tiles"
    const val SUBURBS_LAYER: String = "suburbs"
    const val TILE_SUFFIX: String = ".pbf"
    const val MIME_TYPE: String = "application/vnd.mapbox-vector-tile"

    /** Clients that cannot set headers (the Android map style) may send the API key as this query parameter. */
    const val KEY_QUERY_PARAMETER: String = "key"

    /** Suburbs are served for zoom 5 to 14; below that the map is too wide, above it clients over-zoom level 14. */
    const val SUBURBS_MIN_ZOOM: Int = 5
    const val SUBURBS_MAX_ZOOM: Int = 14

    /** The names of the layers inside a suburb tile: boundary polygons and one label point per suburb. */
    const val SUBURB_POLYGON_LAYER: String = "suburb"
    const val SUBURB_LABEL_LAYER: String = "suburb_label"

    fun suburbsPath(): String = "$TILES_PREFIX/$SUBURBS_LAYER"
}

/** Open endpoint: is the server up, and can it reach its database (when one is configured). */
@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String,
    val database: String = DATABASE_NOT_CONFIGURED,
) {
    companion object {
        const val STATUS_OK: String = "ok"
        const val DATABASE_UP: String = "up"
        const val DATABASE_DOWN: String = "down"
        const val DATABASE_NOT_CONFIGURED: String = "not_configured"
    }
}

/** Authenticated endpoint: reaching it with the right API key proves the key works. */
@Serializable
data class StatusResponse(val authenticated: Boolean)

@Serializable
data class ApiError(val error: String) {
    companion object {
        const val UNAUTHORIZED: String = "unauthorized"
        const val INVALID_TILE: String = "invalid_tile"
        const val UNKNOWN_LAYER: String = "unknown_layer"
        const val UNAVAILABLE: String = "unavailable"
    }
}
