package au.homefinder.server

import au.homefinder.shared.api.TileContract
import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.HealthResponse
import au.homefinder.shared.model.ApiJson
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the real tile SQL against PostGIS. Needs HOMEFINDER_TEST_DB_URL, _USER and _PASSWORD (a throwaway, migrated
 * database); `server/tests/run-db-tests.sh` starts one. Skipped when they are not set.
 */
class PostgisSuburbTilesIntegrationTest {
    private val url = System.getenv("HOMEFINDER_TEST_DB_URL")
    private val user = System.getenv("HOMEFINDER_TEST_DB_USER").orEmpty()
    private val password = System.getenv("HOMEFINDER_TEST_DB_PASSWORD").orEmpty()

    @BeforeEach
    fun seed() {
        assumeTrue(!url.isNullOrBlank(), "no test database configured")
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("TRUNCATE suburb CASCADE")
                statement.execute(box("9001", "HAMILTON", "2303", 151.74, -32.93, 151.76, -32.91))
                statement.execute(box("9002", "BELMONT", "2280", 151.65, -33.05, 151.68, -33.02))
                statement.execute(roundSuburb("9003", "ROUNDVILLE", "2290", 151.45, -32.80, 0.05))
                statement.execute("SELECT refresh_suburb_tile_data()")
            }
        }
    }

    private fun box(id: String, name: String, postcode: String, x0: Double, y0: Double, x1: Double, y1: Double) =
        "INSERT INTO suburb (suburb_id, name, postcode, geom) VALUES ('$id', '$name', '$postcode', " +
            "ST_Multi(ST_GeomFromText('POLYGON(($x0 $y0, $x1 $y0, $x1 $y1, $x0 $y1, $x0 $y0))', 7844)))"

    /** A many-sided suburb, so simplification has something to remove. */
    private fun roundSuburb(id: String, name: String, postcode: String, lon: Double, lat: Double, radius: Double) =
        "INSERT INTO suburb (suburb_id, name, postcode, geom) VALUES ('$id', '$name', '$postcode', " +
            "ST_Multi(ST_Buffer(ST_SetSRID(ST_MakePoint($lon, $lat), 7844), $radius, 64)))"

    private fun scalar(sql: String): Long = DriverManager.getConnection(url, user, password).use { c ->
        c.createStatement().use { st -> st.executeQuery(sql).use { rs -> rs.next(); rs.getLong(1) } }
    }

    private fun tileOf(longitude: Double, latitude: Double, zoom: Int): Pair<Int, Int> {
        val n = 2.0.pow(zoom)
        val sinLat = sin(Math.toRadians(latitude))
        return floor((longitude + 180) / 360 * n).toInt() to floor((0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * n).toInt()
    }

    /** Counts features per layer by walking the protobuf: Tile.layers = 3, Layer.name = 1, Layer.features = 2. */
    private fun featureCounts(tile: ByteArray): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        walk(tile, 0, tile.size) { field, start, end ->
            if (field == 3) {
                var name = ""
                var features = 0
                walk(tile, start, end) { inner, s, e ->
                    if (inner == 1) name = String(tile, s, e - s)
                    if (inner == 2) features++
                }
                counts[name] = features
            }
        }
        return counts
    }

    private fun walk(bytes: ByteArray, from: Int, to: Int, onMessage: (field: Int, start: Int, end: Int) -> Unit) {
        var i = from
        fun varint(): Long { var r = 0L; var shift = 0; while (true) { val b = bytes[i++].toInt(); r = r or ((b and 0x7F).toLong() shl shift); if (b and 0x80 == 0) return r; shift += 7 } }
        while (i < to) {
            val tag = varint().toInt()
            when (tag and 7) {
                0 -> varint()
                2 -> { val length = varint().toInt(); onMessage(tag ushr 3, i, i + length); i += length }
                1 -> i += 8
                5 -> i += 4
                else -> error("wire type ${tag and 7}")
            }
        }
    }

    private fun withTiles(block: suspend (PostgisSuburbTiles) -> Unit) {
        val dataSource = PostgisSuburbTiles.connect(DatabaseConfig(url, user, password))
        try { runBlocking { block(PostgisSuburbTiles(dataSource)) } } finally { dataSource.close() }
    }

    @Test
    fun `a tile over a suburb has its polygon and its label`() = withTiles { tiles ->
        val (x, y) = tileOf(151.75, -32.92, 12)
        val counts = featureCounts(tiles.tile(12, x, y))
        assertEquals(1, counts["suburb"], "polygons $counts")
        assertEquals(1, counts["suburb_label"], "labels $counts")
    }

    @Test
    fun `a wide tile holds every suburb under it with its label`() = withTiles { tiles ->
        val (x, y) = tileOf(151.75, -32.92, 10) // one zoom 10 tile covers both Hamilton and Belmont
        val counts = featureCounts(tiles.tile(10, x, y))
        assertEquals(2, counts["suburb"], "polygons $counts")
        assertEquals(2, counts["suburb_label"], "labels $counts")
    }

    @Test
    fun `each suburb has exactly one label across all the tiles around it`() = withTiles { tiles ->
        val (cx, cy) = tileOf(151.70, -32.97, 12)
        var labels = 0
        for (x in cx - 3..cx + 3) for (y in cy - 3..cy + 3) labels += featureCounts(tiles.tile(12, x, y))["suburb_label"] ?: 0
        assertEquals(3, labels)
    }

    @Test
    fun `a tile away from every suburb is empty`() = withTiles { tiles ->
        val (x, y) = tileOf(151.2093, -33.8688, 10) // Sydney
        assertContentEquals(ByteArray(0), tiles.tile(10, x, y))
    }

    @Test
    fun `wide tiles hold both suburbs and deep tiles only the one under them`() = withTiles { tiles ->
        val (wideX, wideY) = tileOf(151.70, -32.97, 8)
        assertEquals(3, featureCounts(tiles.tile(8, wideX, wideY))["suburb"]) // a zoom 8 tile is 1.4 degrees wide
        val (x, y) = tileOf(151.75, -32.92, 14)
        assertEquals(1, featureCounts(tiles.tile(14, x, y))["suburb"])
    }

    @Test
    fun `the database reports healthy`() = withTiles { tiles ->
        assertTrue(tiles.isHealthy())
    }

    @Test
    fun `tile data is precomputed for every served zoom of every suburb`() {
        assertEquals(3L * (TileContract.SUBURBS_MAX_ZOOM - TileContract.SUBURBS_MIN_ZOOM + 1), scalar("SELECT count(*) FROM suburb_tile_geometry"))
        assertEquals(TileContract.SUBURBS_MIN_ZOOM.toLong(), scalar("SELECT min(zoom) FROM suburb_tile_geometry"))
        assertEquals(TileContract.SUBURBS_MAX_ZOOM.toLong(), scalar("SELECT max(zoom) FROM suburb_tile_geometry"))
        assertEquals(3L, scalar("SELECT count(*) FROM suburb_label"))
    }

    @Test
    fun `wide zooms are simplified more than deep zooms`() {
        val low = scalar("SELECT ST_NPoints(geom) FROM suburb_tile_geometry WHERE suburb_id = '9003' AND zoom = 5")
        val high = scalar("SELECT ST_NPoints(geom) FROM suburb_tile_geometry WHERE suburb_id = '9003' AND zoom = 14")
        assertTrue(low < high, "zoom 5 has $low points, zoom 14 has $high")
    }

    @Test
    fun `refreshing twice gives the same rows`() {
        val before = scalar("SELECT sum(ST_NPoints(geom)) FROM suburb_tile_geometry")
        DriverManager.getConnection(url, user, password).use { it.createStatement().use { s -> s.execute("SELECT refresh_suburb_tile_data()") } }
        assertEquals(before, scalar("SELECT sum(ST_NPoints(geom)) FROM suburb_tile_geometry"))
        assertEquals(3L, scalar("SELECT count(*) FROM suburb_label"))
    }

    @Test
    fun `the API serves a real tile over HTTP with the key as a query parameter`() {
        val apiKey = "0123456789abcdef"
        val dataSource = PostgisSuburbTiles.connect(DatabaseConfig(url, user, password))
        try {
            val (x, y) = tileOf(151.75, -32.92, 12)
            testApplication {
                application { module(ServerConfig("0.0.0.0", 8080, apiKey), PostgisSuburbTiles(dataSource)) }
                val tile = client.get("${TileContract.suburbsPath()}/12/$x/$y.pbf?key=$apiKey")
                assertEquals(HttpStatusCode.OK, tile.status)
                assertEquals(mapOf("suburb" to 1, "suburb_label" to 1), featureCounts(tile.bodyAsBytes()))
                val health = ApiJson.decodeFromString<HealthResponse>(client.get(ApiContract.HEALTH_PATH).bodyAsText())
                assertEquals(HealthResponse.DATABASE_UP, health.database)
                assertEquals(HttpStatusCode.NoContent, client.get("${TileContract.suburbsPath()}/12/0/0.pbf?key=$apiKey").status)
            }
        } finally {
            dataSource.close()
        }
    }
}
