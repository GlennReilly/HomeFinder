package au.homefinder.app.api

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.model.LotRef
import au.homefinder.shared.model.LotReport

/** The lot endpoints of the API (ADR 0022). The key goes only in the request header. */
class LotApi(private val config: ApiConfig, private val http: HttpGet) {
    suspend fun lotAt(latitude: Double, longitude: Double): LookupResult<LotRef> {
        val query = "${ApiContract.LATITUDE_PARAMETER}=${SuburbApi.formatCoordinate(latitude)}&" +
            "${ApiContract.LONGITUDE_PARAMETER}=${SuburbApi.formatCoordinate(longitude)}"
        return getJson(config, http, config.url(ApiContract.LOTS_AT_PATH) + "?" + query)
    }

    suspend fun report(lotId: String): LookupResult<LotReport> =
        getJson(config, http, config.url(ApiContract.LOTS_PATH + "/" + SuburbApi.encodePathSegment(lotId)))
}
