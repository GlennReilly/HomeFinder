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

/** The lot endpoints, inside the API-key guarded `/v1` route. [directory] is null when no database is configured. */
fun Route.lotRoutes(directory: LotDirectory?) {
    get(ApiContract.LOTS_AT_PATH.removePrefix(AUTHENTICATED_PREFIX)) { lotAt(call, directory) }
    get(ApiContract.LOTS_PATH.removePrefix(AUTHENTICATED_PREFIX) + "/{lotId}") { lotReport(call, directory) }
}

private suspend fun lotAt(call: RoutingCall, directory: LotDirectory?) {
    val latitude = call.request.queryParameters[ApiContract.LATITUDE_PARAMETER]?.toDoubleOrNull()
    val longitude = call.request.queryParameters[ApiContract.LONGITUDE_PARAMETER]?.toDoubleOrNull()
    if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() ||
        latitude !in MIN_LATITUDE..MAX_LATITUDE || longitude !in MIN_LONGITUDE..MAX_LONGITUDE
    ) {
        call.respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_COORDINATES))
        return
    }
    respondLot(call, directory) { it.findAt(latitude, longitude)?.toRef() }
}

private suspend fun lotReport(call: RoutingCall, directory: LotDirectory?) {
    val lotId = call.parameters["lotId"].orEmpty()
    respondLot(call, directory) { dir -> dir.find(lotId)?.let { LotReportFactory.build(it, dir.proximity(lotId), dir.terrain(lotId)) } }
}

/** Runs a lookup: 503 without a database or when it fails, 404 when nothing is found, otherwise the result as JSON. */
private suspend inline fun <reified T : Any> respondLot(call: RoutingCall, directory: LotDirectory?, lookup: (LotDirectory) -> T?) {
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
