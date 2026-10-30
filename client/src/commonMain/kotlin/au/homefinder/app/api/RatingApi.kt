package au.homefinder.app.api

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.HomelyRating
import au.homefinder.shared.model.SuburbRatingsResponse
import kotlinx.serialization.encodeToString

private const val HTTP_OK = 200
private const val HTTP_NO_CONTENT = 204
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404

/** The endpoints for your Homely scores (ADR 0028). The key goes only in the request header. */
class RatingApi(private val config: ApiConfig, private val reads: HttpGet, private val writes: HttpWrite) {
    suspend fun all(): LookupResult<SuburbRatingsResponse> = getJson(config, reads, config.url(ApiContract.SUBURB_RATINGS_PATH))

    /** Saves a score; the server rounds the stars to one decimal and answers with what it kept. */
    suspend fun save(suburbId: String, rating: HomelyRating): LookupResult<HomelyRating> {
        val answer = writes.send("PUT", ratingUrl(suburbId), headers(), ApiJson.encodeToString(rating))
        return when (answer) {
            is HttpResult.Failure -> LookupResult.Failed(answer.message)
            is HttpResult.Response -> when (answer.status) {
                HTTP_OK -> runCatching { ApiJson.decodeFromString<HomelyRating>(answer.body) }
                    .fold({ LookupResult.Found(it) }, { LookupResult.Failed("the response was not understood") })
                HTTP_BAD_REQUEST -> LookupResult.Failed("the server did not accept that rating")
                HTTP_UNAUTHORIZED -> LookupResult.Unauthorized
                HTTP_NOT_FOUND -> LookupResult.NotFound
                else -> LookupResult.Failed("the server answered ${answer.status}")
            }
        }
    }

    /** Removes a score. Removing one that is not there is fine. */
    suspend fun remove(suburbId: String): LookupResult<Unit> = when (val answer = writes.send("DELETE", ratingUrl(suburbId), headers(), null)) {
        is HttpResult.Failure -> LookupResult.Failed(answer.message)
        is HttpResult.Response -> when (answer.status) {
            HTTP_NO_CONTENT, HTTP_OK -> LookupResult.Found(Unit)
            HTTP_UNAUTHORIZED -> LookupResult.Unauthorized
            else -> LookupResult.Failed("the server answered ${answer.status}")
        }
    }

    private fun ratingUrl(suburbId: String) = config.url(ApiContract.suburbRatingPath(SuburbApi.encodePathSegment(suburbId)))

    private fun headers() = mapOf(ApiContract.API_KEY_HEADER to config.apiKey)
}
