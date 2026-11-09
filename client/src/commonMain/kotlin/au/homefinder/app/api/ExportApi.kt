package au.homefinder.app.api

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.SuburbExportRequest
import kotlinx.serialization.encodeToString

private const val HTTP_OK = 200
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404

/** Making a PDF of up to 200 suburbs reads every suburb's report, which takes far longer than an ordinary request (ADR 0080). */
const val EXPORT_TIMEOUT_MILLIS: Long = 120_000L

/** What a 400 means, in words: the list was empty, or longer than the server takes. */
const val EXPORT_EMPTY_MESSAGE: String = "there were no suburbs to export"
const val EXPORT_TOO_MANY_MESSAGE: String = "too many suburbs: narrow the filters to ${ApiContract.MAX_EXPORT_SUBURBS} or fewer"
const val EXPORT_NOT_FOUND_MESSAGE: String = "none of the suburbs could be found"

/** The PDF export of the suburbs the filters shade (ADR 0080). The key goes only in the request header. */
class ExportApi(private val config: ApiConfig, private val http: HttpBytes) {
    /** Asks the server for the PDF; the bytes found are the PDF file itself. */
    suspend fun suburbsPdf(request: SuburbExportRequest): LookupResult<ByteArray> {
        val answer = http.fetch(
            HttpVerb.POST,
            config.url(ApiContract.SUBURBS_EXPORT_PDF_PATH),
            mapOf(ApiContract.API_KEY_HEADER to config.apiKey),
            ApiJson.encodeToString(request),
            EXPORT_TIMEOUT_MILLIS,
        )
        return when (answer) {
            is BytesResult.Failure -> LookupResult.Failed(answer.message)
            is BytesResult.Response -> when (answer.status) {
                HTTP_OK -> LookupResult.Found(answer.bytes)
                HTTP_BAD_REQUEST -> LookupResult.Failed(badRequestReason(answer.bytes))
                HTTP_UNAUTHORIZED -> LookupResult.Unauthorized
                HTTP_NOT_FOUND -> LookupResult.Failed(EXPORT_NOT_FOUND_MESSAGE)
                else -> LookupResult.Failed("the server answered ${answer.status}")
            }
        }
    }

    private fun badRequestReason(body: ByteArray): String =
        when (runCatching { ApiJson.decodeFromString<ApiError>(body.decodeToString()).error }.getOrNull()) {
            ApiError.TOO_MANY_SUBURBS -> EXPORT_TOO_MANY_MESSAGE
            else -> EXPORT_EMPTY_MESSAGE
        }
}
