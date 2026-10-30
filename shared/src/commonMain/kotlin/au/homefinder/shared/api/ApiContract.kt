package au.homefinder.shared.api

import kotlinx.serialization.Serializable

/** The parts of the HTTP API that the server and every client must agree on. */
object ApiContract {
    const val HEALTH_PATH: String = "/health"
    const val STATUS_PATH: String = "/v1/status"
    const val API_KEY_HEADER: String = "X-API-Key"

    /** Suburb endpoints: `GET /v1/suburbs/at?lat=&lon=` finds the suburb under a point, `GET /v1/suburbs/{suburbId}` reports on one. */
    const val SUBURBS_PATH: String = "/v1/suburbs"
    const val SUBURBS_AT_PATH: String = "/v1/suburbs/at"
    /** Lot endpoints: `GET /v1/lots/at?lat=&lon=` finds the lot under a point, `GET /v1/lots/{lotId}` reports on one. */
    const val LOTS_PATH: String = "/v1/lots"
    const val LOTS_AT_PATH: String = "/v1/lots/at"
    /**
     * Your own Homely star scores (ADR 0028): `GET /v1/suburbs/ratings` lists every rated suburb with a point for its map
     * label; `PUT` and `DELETE` on `/v1/suburbs/{suburbId}/rating` set and remove one.
     */
    const val SUBURB_RATINGS_PATH: String = "/v1/suburbs/ratings"

    /**
     * The values the filter panel works on (ADR 0035): `GET /v1/suburbs/filters` gives every suburb's roll-up values in one
     * response, so the browser can filter and restyle the map without further requests.
     */
    const val SUBURB_FILTERS_PATH: String = "/v1/suburbs/filters"

    fun suburbRatingPath(encodedSuburbId: String): String = "$SUBURBS_PATH/$encodedSuburbId/rating"

    /** Search: `GET /v1/search?q=` suggests suburbs and addresses for what has been typed so far (ADR 0026). */
    const val SEARCH_PATH: String = "/v1/search"
    const val QUERY_PARAMETER: String = "q"
    const val SEARCH_MIN_QUERY_LENGTH: Int = 2
    const val SEARCH_MAX_QUERY_LENGTH: Int = 100
    const val SEARCH_MAX_RESULTS: Int = 8
    const val LATITUDE_PARAMETER: String = "lat"
    const val LONGITUDE_PARAMETER: String = "lon"
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

    /** Lots are served from zoom 16 (spec: lot boundaries at street level) to 18; clients over-zoom beyond that. */
    const val LOTS_LAYER: String = "lots"
    const val LOTS_MIN_ZOOM: Int = 16
    const val LOTS_MAX_ZOOM: Int = 18

    /** The name of the layer inside a lot tile: lot boundary polygons carrying `lot_id`. */
    const val LOT_POLYGON_LAYER: String = "lot"

    fun suburbsPath(): String = "$TILES_PREFIX/$SUBURBS_LAYER"

    fun lotsPath(): String = "$TILES_PREFIX/$LOTS_LAYER"
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
/** A suburb as the map knows it: enough to select it and name it before its full report is loaded. */
@Serializable
data class SuburbRef(val suburbId: String, val name: String, val postcode: String?)

@Serializable
data class StatusResponse(val authenticated: Boolean)

@Serializable
data class ApiError(val error: String) {
    companion object {
        const val UNAUTHORIZED: String = "unauthorized"
        const val INVALID_COORDINATES: String = "invalid_coordinates"
        const val NOT_FOUND: String = "not_found"
        const val INVALID_TILE: String = "invalid_tile"
        const val UNKNOWN_LAYER: String = "unknown_layer"
        const val UNAVAILABLE: String = "unavailable"
        const val INVALID_QUERY: String = "invalid_query"
        const val INVALID_RATING: String = "invalid_rating"
    }
}
