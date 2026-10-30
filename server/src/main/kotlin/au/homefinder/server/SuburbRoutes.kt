package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException

private const val AUTHENTICATED_PREFIX = "/v1"
private const val MIN_LATITUDE = -90.0
private const val MAX_LATITUDE = 90.0
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0

/** The suburb endpoints, inside the API-key guarded `/v1` route. [directory] is null when no database is configured. */
fun Route.suburbRoutes(directory: SuburbDirectory?, ratings: RatingStore? = null) {
    get(ApiContract.SUBURBS_AT_PATH.removePrefix(AUTHENTICATED_PREFIX)) { suburbAt(call, directory) }
    get(ApiContract.SUBURBS_PATH.removePrefix(AUTHENTICATED_PREFIX) + "/{suburbId}") { suburbReport(call, directory, ratings) }
}

private suspend fun suburbAt(call: RoutingCall, directory: SuburbDirectory?) {
    val latitude = call.request.queryParameters[ApiContract.LATITUDE_PARAMETER]?.toDoubleOrNull()
    val longitude = call.request.queryParameters[ApiContract.LONGITUDE_PARAMETER]?.toDoubleOrNull()
    if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() ||
        latitude !in MIN_LATITUDE..MAX_LATITUDE || longitude !in MIN_LONGITUDE..MAX_LONGITUDE
    ) {
        call.respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_COORDINATES))
        return
    }
    respondFrom(call, directory) { it.findAt(latitude, longitude)?.toRef() }
}

private suspend fun suburbReport(call: RoutingCall, directory: SuburbDirectory?, ratings: RatingStore?) {
    val suburbId = call.parameters["suburbId"].orEmpty()
    respondFrom(call, directory) { dir -> dir.find(suburbId)?.let { SuburbReportFactory.build(it, dir.stats(suburbId), ratings?.get(suburbId), dir.amenity(suburbId)) } }
}

/** Runs a lookup: 503 without a database or when it fails, 404 when nothing is found, otherwise the result as JSON. */
private suspend inline fun <reified T : Any> respondFrom(call: RoutingCall, directory: SuburbDirectory?, lookup: (SuburbDirectory) -> T?) {
    if (directory == null) {
        call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        return
    }
    val found = try {
        lookup(directory)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        return
    }
    if (found == null) call.respond(HttpStatusCode.NotFound, ApiError(ApiError.NOT_FOUND)) else call.respond(found)
}
