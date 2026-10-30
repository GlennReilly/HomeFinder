package au.homefinder.e2e

import au.homefinder.shared.api.SuburbRef
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.GeoBounds
import au.homefinder.shared.model.HomelyRating
import au.homefinder.shared.model.LotRef
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.School
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolSector
import au.homefinder.shared.model.SearchResponse
import au.homefinder.shared.model.SearchResult
import au.homefinder.shared.model.SearchResultKind
import au.homefinder.shared.model.SuburbRatingPoint
import au.homefinder.shared.model.SuburbRatingsResponse
import au.homefinder.shared.model.SuburbReport
import au.homefinder.shared.model.SuburbSchools
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * A stand-in for the whole backend, for the browser smoke tests (ADR 0032): it serves the built web app from [webRoot] and
 * answers the API with a few fixed answers made from the shared models, so a change to the API contract breaks these tests
 * at compile time or on decoding. It insists on the API key, like the real server, and remembers the one Homely score the
 * tests can save. It is not the real server; the server has its own tests.
 */
class FakeApp(private val webRoot: File) : AutoCloseable {
    val apiKey = "smoke-test-key-0123456789"
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val origin: String get() = "http://127.0.0.1:${server.address.port}"

    @Volatile
    var rating: HomelyRating? = null

    @Volatile
    var failSavingRatings: Boolean = false

    private val hamilton = SuburbRef("9001", "HAMILTON", "2303")
    private val lot = LotRef("1001", "9001")
    private val address = "12 MAIN STREET, HAMILTON NSW 2303"

    init {
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
    }

    override fun close() = server.stop(0)

    private fun <T> unknown(source: String = "test") = Attribute.unknown<T>(source, note = "Not computed yet")

    private fun suburbReport() = SuburbReport(
        suburbId = "9001", name = "HAMILTON", postcode = "2303", areaKm2 = Attribute(1.46, Confidence.HIGH, "Test boundary"),
        medianHousePriceAud = unknown(), medianUnitPriceAud = unknown(), priceChange12MonthsPercent = unknown(), salesCount = unknown(),
        blockSize = unknown(), floodShare = unknown(), brickShare = unknown(), beach = unknown(),
        schools = Attribute(
            SuburbSchools(
                listOf(School("1", "Hamilton Public School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, 1051, 412, "https://hamilton-p.schools.nsw.gov.au", "Primary School")),
                emptyList(), 1051,
            ),
            Confidence.HIGH, "Test schools", LocalDate(2026, 10, 2), "ICSEA is not a quality score",
        ),
        parkAreaShare = unknown(), shopping = unknown(), transport = unknown(),
        homelyRating = rating?.let { Attribute(it, Confidence.LOW, HomelyRating.SOURCE, LocalDate(2026, 10, 30), "typed in by you") }
            ?: Attribute.unknown(HomelyRating.SOURCE, note = "Not entered"),
    )

    private fun lotReport() = LotReport(
        "1001", "1//DP100", "9001", "HAMILTON", "2303", listOf(address), Attribute(561.4, Confidence.HIGH, "Test cadastre"),
        unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(),
    )

    private fun search(query: String): SearchResponse {
        val q = query.lowercase()
        val results = buildList {
            if (q.length >= 2 && "hamilton".startsWith(q)) {
                add(SearchResult(SearchResultKind.SUBURB, "9001", "HAMILTON", "Suburb, NSW 2303", -32.92, 151.75, GeoBounds(151.74, -32.93, 151.76, -32.91)))
            }
            if (q.startsWith("12 main")) add(SearchResult(SearchResultKind.ADDRESS, "G1", address, null, -32.92005, 151.7501, lotId = "1001"))
            if (q.startsWith("120 main")) add(SearchResult(SearchResultKind.ADDRESS, "G2", "120 MAIN STREET, HAMILTON NSW 2303", null, -32.921, 151.751))
        }
        return SearchResponse(results)
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.path
            val method = exchange.requestMethod
            when {
                path == "/health" -> json(exchange, 200, """{"status":"ok","service":"Home Finder API","version":"test","database":"up"}""")
                path.startsWith("/v1/") || path.startsWith("/tiles/") -> {
                    if (exchange.requestHeaders.getFirst("X-API-Key") != apiKey) return json(exchange, 401, """{"error":"unauthorized"}""")
                    api(exchange, method, path)
                }
                path.startsWith("/fonts/") -> reply(exchange, 200, "application/x-protobuf", ByteArray(0)) // no glyphs: text is left blank
                else -> staticFile(exchange, path)
            }
        } catch (failure: Exception) {
            reply(exchange, 500, "text/plain", failure.toString().toByteArray())
        } finally {
            exchange.close()
        }
    }

    private fun api(exchange: HttpExchange, method: String, path: String) {
        val query = exchange.requestURI.rawQuery.orEmpty().split('&').filter { '=' in it }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), StandardCharsets.UTF_8) }
        when {
            path.startsWith("/tiles/") -> reply(exchange, 204, "application/vnd.mapbox-vector-tile", ByteArray(0))
            path == "/v1/suburbs/at" -> json(exchange, 200, ApiJson.encodeToString(hamilton))
            path == "/v1/suburbs/ratings" -> json(exchange, 200, ApiJson.encodeToString(SuburbRatingsResponse(ratings())))
            path == "/v1/suburbs/9001/rating" && method == "PUT" -> {
                if (failSavingRatings) return json(exchange, 503, """{"error":"unavailable"}""")
                val saved = ApiJson.decodeFromString<HomelyRating>(exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8))
                rating = saved
                json(exchange, 200, ApiJson.encodeToString(saved))
            }
            path == "/v1/suburbs/9001/rating" && method == "DELETE" -> { rating = null; reply(exchange, 204, "application/json", ByteArray(0)) }
            path == "/v1/suburbs/9001" -> json(exchange, 200, ApiJson.encodeToString(suburbReport()))
            path == "/v1/lots/at" -> json(exchange, 200, ApiJson.encodeToString(lot))
            path == "/v1/lots/1001" -> json(exchange, 200, ApiJson.encodeToString(lotReport()))
            path == "/v1/search" -> json(exchange, 200, ApiJson.encodeToString(search(query["q"].orEmpty())))
            else -> json(exchange, 404, """{"error":"not_found"}""")
        }
    }

    private fun ratings(): List<SuburbRatingPoint> =
        rating?.let { listOf(SuburbRatingPoint("9001", "HAMILTON", it, -32.92, 151.75)) }.orEmpty()

    private fun staticFile(exchange: HttpExchange, requestPath: String) {
        val relative = if (requestPath == "/") "index.html" else requestPath.removePrefix("/")
        val file = File(webRoot, relative).canonicalFile
        // Nothing outside the web root is ever served.
        if (!file.path.startsWith(webRoot.canonicalPath + File.separator) || !file.isFile) return reply(exchange, 404, "text/plain", "not found".toByteArray())
        val type = when (file.extension) {
            "html" -> "text/html; charset=utf-8"
            "js", "mjs" -> "text/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "json", "map" -> "application/json"
            else -> "application/octet-stream"
        }
        reply(exchange, 200, type, file.readBytes())
    }

    private fun json(exchange: HttpExchange, status: Int, body: String) = reply(exchange, status, "application/json", body.toByteArray())

    private fun reply(exchange: HttpExchange, status: Int, type: String, body: ByteArray) {
        exchange.responseHeaders.add("Content-Type", type)
        if (status == 204 || body.isEmpty()) {
            exchange.sendResponseHeaders(status, -1)
        } else {
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }
}
