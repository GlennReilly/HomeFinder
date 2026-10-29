package au.homefinder.app.api

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.HealthResponse
import au.homefinder.shared.model.ApiJson

sealed interface ApiStatus {
    data object Checking : ApiStatus
    data class Online(val version: String) : ApiStatus
    data object KeyMissing : ApiStatus
    data object KeyRejected : ApiStatus
    data class Unreachable(val reason: String) : ApiStatus
    data class BadResponse(val reason: String) : ApiStatus

    /** A short line for the status badge. */
    fun label(): String = when (this) {
        Checking -> "API: checking…"
        is Online -> "API: online (v$version)"
        KeyMissing -> "API: reachable, no API key set"
        KeyRejected -> "API: reachable, key rejected"
        is Unreachable -> "API: unreachable ($reason)"
        is BadResponse -> "API: unexpected response ($reason)"
    }
}

/**
 * Asks the open health endpoint whether the server is up, then the authenticated status endpoint whether the key
 * works. The key is sent only to the second request.
 */
class ApiStatusChecker(private val config: ApiConfig, private val http: HttpGet) {
    suspend fun check(): ApiStatus {
        val health = when (val result = http.get(config.url(ApiContract.HEALTH_PATH), emptyMap())) {
            is HttpResult.Failure -> return ApiStatus.Unreachable(result.message)
            is HttpResult.Response -> result
        }
        if (health.status != HTTP_OK) return ApiStatus.BadResponse("health returned ${health.status}")
        val version = runCatching { ApiJson.decodeFromString<HealthResponse>(health.body).version }
            .getOrElse { return ApiStatus.BadResponse("health body was not understood") }

        if (config.apiKey.isBlank()) return ApiStatus.KeyMissing

        return when (val result = http.get(config.url(ApiContract.STATUS_PATH), mapOf(ApiContract.API_KEY_HEADER to config.apiKey))) {
            is HttpResult.Failure -> ApiStatus.Unreachable(result.message)
            is HttpResult.Response -> when (result.status) {
                HTTP_OK -> ApiStatus.Online(version)
                HTTP_UNAUTHORIZED -> ApiStatus.KeyRejected
                else -> ApiStatus.BadResponse("status returned ${result.status}")
            }
        }
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
    }
}
