package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.model.NaplanCheck
import au.homefinder.shared.model.NaplanEntry
import au.homefinder.shared.model.NaplanRules
import au.homefinder.shared.model.SchoolNaplan
import au.homefinder.shared.model.SchoolNaplanResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.coroutines.CancellationException

private const val AUTHENTICATED_PREFIX = "/v1"
private const val CODE_SEPARATOR = ','

/** The most school codes one list request may name; a card lists far fewer. */
private const val MAX_CODES = 200

/**
 * Your NAPLAN figures (ADR 0068), inside the API-key guarded `/v1` route: list them (all, or `?codes=` for some schools), set one and remove
 * one. Nothing is fetched from My School. [store] is null when no database is configured.
 */
fun Route.naplanRoutes(store: NaplanStore?) {
    get(ApiContract.SCHOOL_NAPLAN_PATH.removePrefix(AUTHENTICATED_PREFIX)) {
        if (store == null) return@get call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        val codes = call.request.queryParameters[ApiContract.CODES_PARAMETER]?.split(CODE_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }
        if (codes != null && codes.size > MAX_CODES) return@get call.respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_QUERY))
        val entries = if (codes == null) {
            call.attempt { store.all() }.getOrNull() ?: return@get
        } else {
            val found = call.attempt { store.forSchools(codes) }.getOrNull() ?: return@get
            found.map { (code, naplan) -> SchoolNaplan(code, naplan) }.sortedBy { it.schoolCode.toIntOrNull() }
        }
        call.respond(SchoolNaplanResponse(entries))
    }
    put(ApiContract.SCHOOLS_PATH.removePrefix(AUTHENTICATED_PREFIX) + "/{schoolCode}/naplan") {
        if (store == null) return@put call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        val code = call.parameters["schoolCode"].orEmpty()
        val entry = receiveEntry(call) ?: return@put
        if (PostgisNaplanStore.schoolCodeOf(code) == null) return@put call.respond(HttpStatusCode.NotFound, ApiError(ApiError.NOT_FOUND))
        when (val written = call.attempt { store.put(code, entry) }.getOrNull() ?: return@put) {
            is NaplanWrite.Saved -> call.respond(written.naplan)
            NaplanWrite.UnknownSchool -> call.respond(HttpStatusCode.NotFound, ApiError(ApiError.NOT_FOUND))
        }
    }
    delete(ApiContract.SCHOOLS_PATH.removePrefix(AUTHENTICATED_PREFIX) + "/{schoolCode}/naplan") {
        if (store == null) return@delete call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        if (call.attempt { store.delete(call.parameters["schoolCode"].orEmpty()) }.isFailure) return@delete
        call.respond(HttpStatusCode.NoContent)
    }
}

/** The body as a checked entry, or a 400 and null: not JSON, not whole numbers, or outside [NaplanRules]. */
private suspend fun receiveEntry(call: RoutingCall): NaplanEntry? {
    val received = try {
        call.receive<NaplanEntry>()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") invalid: Exception) {
        call.respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_NAPLAN))
        return null
    }
    return when (val checked = NaplanRules.check(received)) {
        is NaplanCheck.Valid -> checked.entry
        is NaplanCheck.Invalid -> {
            call.respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_NAPLAN))
            null
        }
    }
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
