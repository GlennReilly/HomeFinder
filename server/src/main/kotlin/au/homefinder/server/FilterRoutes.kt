package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.model.FilterProvenance
import au.homefinder.shared.model.SuburbFilterValue
import au.homefinder.shared.model.SuburbFiltersResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException

private const val AUTHENTICATED_PREFIX = "/v1"

/** The filter values are computed afresh on each request and include your own NAPLAN figures, so no copy may be reused. */
const val FILTERS_CACHE_CONTROL: String = "no-store"

/**
 * The values the filter panel works on (ADR 0035, 0046), inside the API-key guarded `/v1` route. A criterion whose data has not been
 * loaded has no provenance in the response and no values, so the panel can say the data is missing.
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
        // The NAPLAN values change whenever a figure is saved (ADR 0074), so the response is never kept by the browser.
        call.response.header(HttpHeaders.CacheControl, FILTERS_CACHE_CONTROL)
        call.respond(
            SuburbFiltersResponse(
                suburbs = data?.suburbs.orEmpty().map { SuburbFilterValue(it.suburbId, it.averageIcsea, it.beachKm, it.parkShare, it.rentedShare, it.seifaDecile, it.violentPer1000, it.propertyPer1000, it.name, it.zonedIcsea, it.zonedNaplan, it.latitude, it.longitude) },
                icseaSource = SuburbReportFactory.SCHOOLS_SOURCE,
                icseaAsOf = data?.icseaAsOf,
                beach = data?.takeIf { it.osmLoaded }?.let { FilterProvenance(LotReportFactory.OSM_SOURCE, it.osmAsOf) },
                parks = data?.takeIf { it.osmLoaded }?.let { FilterProvenance(LotReportFactory.OSM_SOURCE, it.osmAsOf) },
                rented = data?.takeIf { it.censusLoaded }?.let { FilterProvenance(SuburbReportFactory.CENSUS_SOURCE, SuburbReportFactory.CENSUS_DATE) },
                seifa = data?.takeIf { it.seifaLoaded }?.let { FilterProvenance(SuburbReportFactory.SEIFA_SOURCE, SuburbReportFactory.CENSUS_DATE) },
                crime = data?.takeIf { it.crimeLoaded }?.let { FilterProvenance(SuburbReportFactory.CRIME_SOURCE, it.crimeAsOf) },
                zoned = data?.takeIf { it.zonedLoaded }?.let { FilterProvenance(SuburbReportFactory.ZONED_FILTER_SOURCE, it.zonedAsOf) },
                naplan = data?.takeIf { it.naplanLoaded }?.let { FilterProvenance(SuburbReportFactory.NAPLAN_FILTER_SOURCE, it.naplanAsOf) },
            ),
        )
    }
}
