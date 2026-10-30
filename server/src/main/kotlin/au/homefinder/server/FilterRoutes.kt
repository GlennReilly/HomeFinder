package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.model.SuburbFilterValue
import au.homefinder.shared.model.SuburbFiltersResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException

private const val AUTHENTICATED_PREFIX = "/v1"

/**
 * The values the filter panel works on (ADR 0035), inside the API-key guarded `/v1` route. When the schools data has not
 * been loaded every suburb is listed with no ICSEA and no as-of date, so the panel can say the data is missing.
 */
fun Route.filterRoutes(directory: SuburbDirectory?) {
    get(ApiContract.SUBURB_FILTERS_PATH.removePrefix(AUTHENTICATED_PREFIX)) {
        if (directory == null) return@get call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        val data = try {
            directory.filterValues()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            return@get call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        }
        call.respond(
            SuburbFiltersResponse(
                suburbs = data?.suburbs.orEmpty().map { SuburbFilterValue(it.suburbId, it.averageIcsea) },
                icseaSource = SuburbReportFactory.SCHOOLS_SOURCE,
                icseaAsOf = data?.icseaAsOf,
            ),
        )
    }
}
