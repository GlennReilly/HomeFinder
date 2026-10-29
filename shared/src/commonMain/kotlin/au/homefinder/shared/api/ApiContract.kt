package au.homefinder.shared.api

import kotlinx.serialization.Serializable

/** The parts of the HTTP API that the server and every client must agree on. */
object ApiContract {
    const val HEALTH_PATH: String = "/health"
    const val STATUS_PATH: String = "/v1/status"
    const val API_KEY_HEADER: String = "X-API-Key"
    const val DEFAULT_PORT: Int = 8080
}

/** Open endpoint: is the server up. */
@Serializable
data class HealthResponse(val status: String, val service: String, val version: String) {
    companion object {
        const val STATUS_OK: String = "ok"
    }
}

/** Authenticated endpoint: reaching it with the right API key proves the key works. */
@Serializable
data class StatusResponse(val authenticated: Boolean)

@Serializable
data class ApiError(val error: String) {
    companion object {
        const val UNAUTHORIZED: String = "unauthorized"
    }
}
