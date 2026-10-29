package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
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

/** Every route under this one needs the right [ApiContract.API_KEY_HEADER]; otherwise the call ends with 401. */
fun Route.requireApiKey(expectedKey: String) {
    intercept(ApplicationCallPipeline.Plugins) {
        if (!ApiKeyCheck.matches(call.request.headers[ApiContract.API_KEY_HEADER], expectedKey)) {
            call.respond(HttpStatusCode.Unauthorized, ApiError(ApiError.UNAUTHORIZED))
            finish()
        }
    }
}
