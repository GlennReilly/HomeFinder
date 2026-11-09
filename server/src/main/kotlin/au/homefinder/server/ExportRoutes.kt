package au.homefinder.server

import au.homefinder.app.selection.CardLayout
import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.ApiError
import au.homefinder.shared.model.SuburbExportRequest
import au.homefinder.shared.model.SuburbReport
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

private const val AUTHENTICATED_PREFIX = "/v1"

/** The PDF holds your own figures (NAPLAN, Homely) and is made afresh each time, so no copy may be kept. */
const val EXPORT_CACHE_CONTROL: String = "no-store"

/** The most filter lines, and the longest line, the PDF takes from a request; the panel sends one short line per switched-on filter. */
const val MAX_EXPORT_FILTER_LINES: Int = 20
const val MAX_EXPORT_FILTER_CHARS: Int = 300

/** The longest card layout text accepted; a real one is a few hundred characters. */
const val MAX_EXPORT_LAYOUT_CHARS: Int = 10_000

/**
 * The PDF export of the suburbs the filters shade (ADR 0080), inside the API-key guarded `/v1` route. 400 when the body is not a request, names no
 * suburb or more than [ApiContract.MAX_EXPORT_SUBURBS]; 404 when none of them is found; 503 without a database or when a lookup fails. Suburbs not found
 * are left out and counted on the cover.
 */
fun Route.exportRoutes(directory: SuburbDirectory?, ratings: RatingStore?, naplan: NaplanStore?, today: () -> LocalDate) {
    post(ApiContract.SUBURBS_EXPORT_PDF_PATH.removePrefix(AUTHENTICATED_PREFIX)) {
        val request = try {
            call.receive<SuburbExportRequest>()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") invalid: Exception) {
            return@post call.respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_EXPORT))
        }
        val ids = request.suburbIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty() || request.cardLayout.length > MAX_EXPORT_LAYOUT_CHARS) {
            return@post call.respond(HttpStatusCode.BadRequest, ApiError(ApiError.INVALID_EXPORT))
        }
        if (ids.size > ApiContract.MAX_EXPORT_SUBURBS) return@post call.respond(HttpStatusCode.BadRequest, ApiError(ApiError.TOO_MANY_SUBURBS))
        if (directory == null) return@post call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        val reports: List<SuburbReport?> = try {
            ids.map { loadSuburbReport(directory, it, ratings, naplan) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            return@post call.respond(HttpStatusCode.ServiceUnavailable, ApiError(ApiError.UNAVAILABLE))
        }
        val found = reports.filterNotNull()
        if (found.isEmpty()) return@post call.respond(HttpStatusCode.NotFound, ApiError(ApiError.NOT_FOUND))
        val layout = CardLayout.decode(request.cardLayout)
        val date = today().toString()
        val document = ExportDocument(
            date = date,
            filters = request.filters.take(MAX_EXPORT_FILTER_LINES).map { it.take(MAX_EXPORT_FILTER_CHARS) },
            suburbs = found.map { SuburbExportContent.suburb(it, layout) },
            notFound = ids.size - found.size,
        )
        val bytes = withContext(Dispatchers.Default) { SuburbPdf.render(document) }
        call.response.header(HttpHeaders.CacheControl, EXPORT_CACHE_CONTROL)
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, ApiContract.exportFileName(date)).toString(),
        )
        call.respondBytes(bytes, ContentType.parse(ApiContract.PDF_CONTENT_TYPE))
    }
}
