package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.model.SearchResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException

private const val AUTHENTICATED_PREFIX = "/v1"

/**
 * `GET /v1/search?q=`: suggestions for what has been typed so far. A query shorter than the minimum or longer than the
 * maximum is a bad request; a query with no letters or digits has no suggestions. [index] is null when no database is
 * configured.
 */
fun Route.searchRoutes(index: SearchIndex?) {
    get(ApiContract.SEARCH_PATH.removePrefix(AUTHENTICATED_PREFIX)) {
        val text = call.request.queryParameters[ApiContract.QUERY_PARAMETER]?.trim().orEmpty()
        if (text.length < ApiContract.SEARCH_MIN_QUERY_LENGTH || text.length > ApiContract.SEARCH_MAX_QUERY_LENGTH) {
            call.respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_QUERY))
            return@get
        }
        if (index == null) {
            call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
            return@get
        }
        val results = try {
            index.search(SearchQuery.parse(text), ApiContract.SEARCH_MAX_RESULTS)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
            return@get
        }
        call.respond(SearchResponse(results))
    }
}
