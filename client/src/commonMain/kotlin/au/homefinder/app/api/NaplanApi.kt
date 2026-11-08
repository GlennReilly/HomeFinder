package au.homefinder.app.api

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.NaplanEntry
import au.homefinder.shared.model.NaplanScore
import kotlinx.serialization.encodeToString

private const val HTTP_OK = 200
private const val HTTP_NO_CONTENT = 204
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404

/** The endpoints for your NAPLAN figures, one per school (ADR 0068). The key goes only in the request header. */
class NaplanApi(private val config: ApiConfig, private val writes: HttpWrite) {
    /** Saves a figure; the server answers with what it kept (the note trimmed, the day added). */
    suspend fun save(schoolCode: String, entry: NaplanEntry): LookupResult<NaplanScore> =
        when (val answer = writes.send("PUT", naplanUrl(schoolCode), headers(), ApiJson.encodeToString(entry))) {
            is HttpResult.Failure -> LookupResult.Failed(answer.message)
            is HttpResult.Response -> when (answer.status) {
                HTTP_OK -> runCatching { ApiJson.decodeFromString<NaplanScore>(answer.body) }
                    .fold({ LookupResult.Found(it) }, { LookupResult.Failed("the response was not understood") })
                HTTP_BAD_REQUEST -> LookupResult.Failed("the server did not accept that figure")
                HTTP_UNAUTHORIZED -> LookupResult.Unauthorized
                HTTP_NOT_FOUND -> LookupResult.NotFound
                else -> LookupResult.Failed("the server answered ${answer.status}")
            }
        }

    /** Removes a figure. Removing one that is not there is fine. */
    suspend fun remove(schoolCode: String): LookupResult<Unit> = when (val answer = writes.send("DELETE", naplanUrl(schoolCode), headers(), null)) {
        is HttpResult.Failure -> LookupResult.Failed(answer.message)
        is HttpResult.Response -> when (answer.status) {
            HTTP_NO_CONTENT, HTTP_OK -> LookupResult.Found(Unit)
            HTTP_UNAUTHORIZED -> LookupResult.Unauthorized
            else -> LookupResult.Failed("the server answered ${answer.status}")
        }
    }

    private fun naplanUrl(schoolCode: String) = config.url(ApiContract.schoolNaplanPath(SuburbApi.encodePathSegment(schoolCode)))

    private fun headers() = mapOf(ApiContract.API_KEY_HEADER to config.apiKey)
}
