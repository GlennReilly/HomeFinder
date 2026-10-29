package au.homefinder.server

import au.homefinder.shared.AppInfo
import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.HealthResponse
import au.homefinder.shared.api.StatusResponse
import au.homefinder.shared.model.ApiJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

private const val AUTHENTICATED_PREFIX = "/v1"

/** The API: an open health check and, behind the API key, everything else (endpoints arrive in later PRs). */
fun Application.module(config: ServerConfig) {
    install(ContentNegotiation) { json(ApiJson) }
    routing {
        get(ApiContract.HEALTH_PATH) {
            call.respond(HealthResponse(HealthResponse.STATUS_OK, ServerInfo.name, AppInfo.VERSION))
        }
        route(AUTHENTICATED_PREFIX) {
            requireApiKey(config.apiKey)
            get(ApiContract.STATUS_PATH.removePrefix(AUTHENTICATED_PREFIX)) {
                call.respond(StatusResponse(authenticated = true))
            }
        }
    }
}
