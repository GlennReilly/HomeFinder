package au.homefinder.app.api

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.model.SuburbFiltersResponse

/** The endpoint behind the filter panel (ADR 0035): every suburb's filter values in one request. */
class FilterApi(private val config: ApiConfig, private val http: HttpGet) {
    suspend fun values(): LookupResult<SuburbFiltersResponse> = getJson(config, http, config.url(ApiContract.SUBURB_FILTERS_PATH))
}
