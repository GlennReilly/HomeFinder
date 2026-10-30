package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.model.HomelyRating
import au.homefinder.shared.model.SuburbRatingsResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong

private const val AUTHENTICATED_PREFIX = "/v1"
private const val STAR_STEPS_PER_UNIT = 10.0

/**
 * Your Homely scores (ADR 0028), inside the API-key guarded `/v1` route: list them, set one and remove one. The only
 * writes the API has. [store] and [directory] are null when no database is configured; [today] dates an entry.
 */
fun Route.ratingRoutes(store: RatingStore?, directory: SuburbDirectory?, today: () -> LocalDate) {
    get(ApiContract.SUBURB_RATINGS_PATH.removePrefix(AUTHENTICATED_PREFIX)) {
        if (store == null) return@get call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        val ratings = call.attempt { store.all() }.getOrNull() ?: return@get
        call.respond(SuburbRatingsResponse(ratings))
    }
    put(ApiContract.SUBURBS_PATH.removePrefix(AUTHENTICATED_PREFIX) + "/{suburbId}/rating") {
        if (store == null || directory == null) return@put call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        val suburbId = call.parameters["suburbId"].orEmpty()
        val rating = receiveRating(call) ?: return@put
        val exists = call.attempt { directory.find(suburbId) != null }.getOrNull() ?: return@put
        if (!exists) return@put call.respond(HttpStatusCode.NotFound, ApiError(ApiError.NOT_FOUND))
        if (call.attempt { store.put(suburbId, rating, today()) }.isFailure) return@put
        call.respond(rating)
    }
    delete(ApiContract.SUBURBS_PATH.removePrefix(AUTHENTICATED_PREFIX) + "/{suburbId}/rating") {
        if (store == null) return@delete call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        if (call.attempt { store.delete(call.parameters["suburbId"].orEmpty()) }.isFailure) return@delete
        call.respond(HttpStatusCode.NoContent)
    }
}

/** The body as a rating with the stars rounded to one decimal place, or a 400 and null (not a number, out of range, not JSON). */
private suspend fun receiveRating(call: RoutingCall): HomelyRating? {
    val received = try {
        call.receive<HomelyRating>()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") invalid: Exception) {
        call.respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_RATING))
        return null
    }
    return received.copy(stars = (received.stars * STAR_STEPS_PER_UNIT).roundToLong() / STAR_STEPS_PER_UNIT)
}

/** Runs [block]. A failure answers 503 here and comes back as a failed [Result], so the caller just stops. */
private suspend fun <T> RoutingCall.attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
    respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
    Result.failure(failure)
}
