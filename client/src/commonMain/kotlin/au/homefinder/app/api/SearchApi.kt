package au.homefinder.app.api

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.model.SearchResponse

/** The search endpoint of the API (ADR 0026). The key goes only in the request header; the text is percent-encoded. */
class SearchApi(private val config: ApiConfig, private val http: HttpGet) {
    suspend fun search(text: String): LookupResult<SearchResponse> =
        getJson(config, http, config.url(ApiContract.SEARCH_PATH) + "?${ApiContract.QUERY_PARAMETER}=" + SuburbApi.encodePathSegment(text))
}
