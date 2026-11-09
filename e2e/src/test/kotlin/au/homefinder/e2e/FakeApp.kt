package au.homefinder.e2e

import au.homefinder.shared.api.SuburbRef
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.SuburbShopping
import au.homefinder.shared.model.BlockSizeSpread
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.GeoBounds
import au.homefinder.shared.model.HomelyRating
import au.homefinder.shared.model.LotRef
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.NaplanEntry
import au.homefinder.shared.model.NaplanScore
import au.homefinder.shared.model.School
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolSector
import au.homefinder.shared.model.SearchResponse
import au.homefinder.shared.model.SearchResult
import au.homefinder.shared.model.SearchResultKind
import au.homefinder.shared.model.SlopeClass
import au.homefinder.shared.model.SlopeSummary
import au.homefinder.shared.model.FilterProvenance
import au.homefinder.shared.model.SuburbFilterValue
import au.homefinder.shared.model.SuburbFiltersResponse
import au.homefinder.shared.model.SuburbRatingPoint
import au.homefinder.shared.model.SuburbRatingsResponse
import au.homefinder.shared.model.SuburbReport
import au.homefinder.shared.model.SuburbSchools
import au.homefinder.shared.model.SuburbZone
import au.homefinder.shared.model.SuburbZonedSchools
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

    /** The NAPLAN figures typed in, by school code (ADR 0068); only the fake's schools 1 and 2 can have one. */
    val naplan: MutableMap<String, NaplanScore> = java.util.concurrent.ConcurrentHashMap()

    private val hamilton = SuburbRef("9001", "HAMILTON", "2303")
    private val lot = LotRef("1001", "9001")
    private val address = "12 MAIN STREET, HAMILTON NSW 2303"

    init {
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
    }

    override fun close() = server.stop(0)

    private fun <T> unknown(source: String = "test") = Attribute.unknown<T>(source, note = "Not computed yet")

    private fun suburbReport(id: String = "9001", name: String = "HAMILTON", postcode: String = "2303") = SuburbReport(
        suburbId = id, name = name, postcode = postcode, areaKm2 = Attribute(1.46, Confidence.HIGH, "Test boundary"),
        // The rows a real suburb would have data for are known; the rest stay unknown, so the card leaves them off (ADR 0058).
        medianHousePriceAud = Attribute(900_000L, Confidence.HIGH, "Test sales", LocalDate(2026, 10, 9)),
        medianUnitPriceAud = Attribute(600_000L, Confidence.MEDIUM, "Test sales", LocalDate(2026, 10, 9)),
        priceChange12MonthsPercent = Attribute(4.2, Confidence.MEDIUM, "Test sales", LocalDate(2026, 10, 9)),
        salesCount = Attribute(120, Confidence.HIGH, "Test sales", LocalDate(2026, 10, 9)),
        blockSize = Attribute(BlockSizeSpread(560.0, 300.0, 900.0), Confidence.HIGH, "Test cadastre"), floodShare = unknown(), brickShare = unknown(), beach = unknown(),
        schools = Attribute(
            SuburbSchools(
                listOf(School("1", "Hamilton Public School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, 1051, 412, "https://hamilton-p.schools.nsw.gov.au", "Primary School", naplan["1"], locality = "Hamilton")),
                emptyList(), 1051,
            ),
            Confidence.HIGH, "Test schools", LocalDate(2026, 10, 2), "ICSEA is not a quality score",
        ),
        zonedSchools = Attribute(
            SuburbZonedSchools(
                listOf(
                    SuburbZone(School("1", "Hamilton South Public School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, 1041, null, null, "primary, K-6", naplan["1"]), 0.82),
                    SuburbZone(School("2", "Newcastle High School", SchoolSector.PUBLIC, SchoolLevel.HIGH, 1017, null, null, "high, 7-12", naplan["2"]), 0.76),
                ),
                2026,
            ),
            Confidence.MEDIUM, "Test catchments", null, "Catchments change",
        ),
        parkAreaShare = Attribute(0.12, Confidence.MEDIUM, "Test OpenStreetMap", LocalDate(2026, 10, 29)),
        shopping = Attribute(SuburbShopping(1, 2), Confidence.MEDIUM, "Test OpenStreetMap", LocalDate(2026, 10, 29)), transport = unknown(),
        homelyRating = rating?.let { Attribute(it, Confidence.LOW, HomelyRating.SOURCE, LocalDate(2026, 10, 30), "typed in by you") }
            ?: Attribute.unknown(HomelyRating.SOURCE, note = "Not entered"),
    )

    private fun lotReport() = LotReport(
        "1001", "1//DP100", "9001", "HAMILTON", "2303", listOf(address), Attribute(561.4, Confidence.HIGH, "Test cadastre"),
        unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(),
    ).copy(
        slope = Attribute(SlopeSummary(12.04, 21.3, SlopeClass.MODERATE), Confidence.MEDIUM, "Test DEM", LocalDate(2026, 10, 30)),
        elevationM = Attribute(35.04, Confidence.MEDIUM, "Test DEM", LocalDate(2026, 10, 30)),
    )

    private fun search(query: String): SearchResponse {
        val q = query.lowercase()
        val results = buildList {
            if (q.length >= 2 && "hamilton".startsWith(q)) {
                add(SearchResult(SearchResultKind.SUBURB, "9001", "HAMILTON", "Suburb, NSW 2303", -32.92, 151.75, GeoBounds(151.74, -32.93, 151.76, -32.91)))
            }
            if (q.length >= 2 && "mayfield".startsWith(q)) {
                add(SearchResult(SearchResultKind.SUBURB, "9002", "MAYFIELD", "Suburb, NSW 2304", -32.89, 151.74, GeoBounds(151.73, -32.90, 151.75, -32.88)))
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
            path == "/v1/suburbs/filters" -> json(exchange, 200, ApiJson.encodeToString(filters()))
            path == "/v1/suburbs/ratings" -> json(exchange, 200, ApiJson.encodeToString(SuburbRatingsResponse(ratings())))
            path == "/v1/suburbs/9001/rating" && method == "PUT" -> {
                if (failSavingRatings) return json(exchange, 503, """{"error":"unavailable"}""")
                val saved = ApiJson.decodeFromString<HomelyRating>(exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8))
                rating = saved
                json(exchange, 200, ApiJson.encodeToString(saved))
            }
            path == "/v1/suburbs/9001/rating" && method == "DELETE" -> { rating = null; reply(exchange, 204, "application/json", ByteArray(0)) }
            path.matches(Regex("/v1/schools/[12]/naplan")) && method == "PUT" -> {
                val entry = ApiJson.decodeFromString<NaplanEntry>(exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8))
                val kept = NaplanScore(entry.score, entry.year, LocalDate(2026, 11, 8))
                naplan[path.split('/')[3]] = kept
                json(exchange, 200, ApiJson.encodeToString(kept))
            }
            path.matches(Regex("/v1/schools/[12]/naplan")) && method == "DELETE" -> { naplan.remove(path.split('/')[3]); reply(exchange, 204, "application/json", ByteArray(0)) }
            path == "/v1/suburbs/9001" -> json(exchange, 200, ApiJson.encodeToString(suburbReport()))
            path == "/v1/suburbs/9002" -> json(exchange, 200, ApiJson.encodeToString(suburbReport("9002", "MAYFIELD", "2304")))
            path == "/v1/lots/at" -> json(exchange, 200, ApiJson.encodeToString(lot))
            path == "/v1/lots/1001" -> json(exchange, 200, ApiJson.encodeToString(lotReport()))
            path == "/v1/search" -> json(exchange, 200, ApiJson.encodeToString(search(query["q"].orEmpty())))
            else -> json(exchange, 404, """{"error":"not_found"}""")
        }
    }

    /** Three suburbs: one above 1,000 (and near the beach, with parks, mostly rented), one below, one with no values at all. */
    private fun filters() = SuburbFiltersResponse(
        listOf(
            SuburbFilterValue("9001", 1051, beachKm = 1.5, parkShare = 0.20, rentedShare = 0.45, seifaDecile = 8, violentPer1000 = 4.2, propertyPer1000 = 12.5, name = "HAMILTON", zonedIcsea = 1030),
            SuburbFilterValue("9002", 960, beachKm = 12.0, parkShare = 0.05, rentedShare = 0.20, seifaDecile = 3, violentPer1000 = 31.0, propertyPer1000 = 90.0, name = "MAYFIELD", zonedIcsea = 990),
            SuburbFilterValue("9003", null, name = "WICKHAM"),
        ),
        "Test schools", LocalDate(2026, 10, 2),
        beach = FilterProvenance("Test OpenStreetMap", LocalDate(2026, 10, 29)),
        parks = FilterProvenance("Test OpenStreetMap", LocalDate(2026, 10, 29)),
        rented = FilterProvenance("Test Census", LocalDate(2021, 8, 10)),
        seifa = FilterProvenance("Test SEIFA", LocalDate(2021, 8, 10)),
        crime = FilterProvenance("Test BOCSAR", LocalDate(2026, 6, 30)),
        zoned = FilterProvenance("Test catchments", LocalDate(2026, 11, 5)),
    )

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
