package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.api.TileContract
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.intercept
import java.security.MessageDigest

/** Compares API keys by their SHA-256 digests, so the time taken does not depend on how much of the key matches. */
object ApiKeyCheck {
    fun matches(provided: String?, expected: String): Boolean {
        if (provided == null) return false
        val digest = MessageDigest.getInstance("SHA-256")
        return MessageDigest.isEqual(digest.digest(provided.toByteArray()), digest.digest(expected.toByteArray()))
    }
}

/**
 * Every route under this one needs the right [ApiContract.API_KEY_HEADER]; otherwise the call ends with 401. With
 * [acceptQueryParameter] the key may instead come as the [TileContract.KEY_QUERY_PARAMETER] query parameter, for
 * clients such as the Android map style that cannot set headers (the header wins if both are sent).
 */
fun Route.requireApiKey(expectedKey: String, acceptQueryParameter: Boolean = false) {
    intercept(ApplicationCallPipeline.Plugins) {
        val provided = call.request.headers[ApiContract.API_KEY_HEADER]
            ?: if (acceptQueryParameter) call.request.queryParameters[TileContract.KEY_QUERY_PARAMETER] else null
        if (!ApiKeyCheck.matches(provided, expectedKey)) {
            call.respond(HttpStatusCode.Unauthorized, ApiError(ApiError.UNAUTHORIZED))
            finish()
        }
    }
}
