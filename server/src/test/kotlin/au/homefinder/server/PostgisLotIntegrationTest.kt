package au.homefinder.server

import au.homefinder.shared.api.TileContract
import java.sql.DriverManager
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the real lot tiles and lookups against PostGIS; see [PostgisSuburbTilesIntegrationTest] for how to supply a database. */
class PostgisLotIntegrationTest {
    private val url = System.getenv("HOMEFINDER_TEST_DB_URL")
    private val user = System.getenv("HOMEFINDER_TEST_DB_USER").orEmpty()
    private val password = System.getenv("HOMEFINDER_TEST_DB_PASSWORD").orEmpty()

    private fun box(x0: Double, y0: Double, x1: Double, y1: Double) =
        "ST_Multi(ST_GeomFromText('POLYGON(($x0 $y0, $x1 $y0, $x1 $y1, $x0 $y1, $x0 $y0))', 7844))"

    private fun lot(id: String, label: String, key: String, geom: String) =
        "INSERT INTO lot (lot_id, lot_id_string, parcel_key, plan_label, suburb_id, geom) VALUES ('$id', '$label', '$key', 'DP1', '9001', $geom)"

    private fun address(pid: String, flat: String?, number: String, lon: Double, lat: Double) =
        "INSERT INTO address (gnaf_pid, flat_number, number_first, street_name, street_type, locality_name, state, postcode, source_release, geom) " +
            "VALUES ('$pid', ${flat?.let { "'$it'" } ?: "NULL"}, '$number', 'MAIN', 'STREET', 'HAMILTON', 'NSW', '2303', 'test', ST_SetSRID(ST_MakePoint($lon, $lat), 7844))"

    @BeforeEach
    fun seed() {
        assumeTrue(!url.isNullOrBlank(), "no test database configured")
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("TRUNCATE suburb, address CASCADE")
                statement.execute(
                    "INSERT INTO suburb (suburb_id, name, postcode, geom) VALUES ('9001', 'HAMILTON', '2303', ${box(151.74, -32.93, 151.76, -32.91)})",
                )
                // Lot 1001: about 18.6 m x 33 m; 1002 is its neighbour; 1003 is a strata parcel; 1004 is a huge lot around both.
                statement.execute(lot("1001", "1//DP100", "1/100", box(151.7500, -32.9203, 151.7502, -32.9200)))
                statement.execute(lot("1002", "2//DP100", "2/100", box(151.7502, -32.9203, 151.7504, -32.9200)))
                statement.execute(lot("1003", "1//SP555", "1/SP555", box(151.7510, -32.9203, 151.7513, -32.9200)))
                statement.execute(lot("1004", "9//DP9", "9/9", box(151.7490, -32.9210, 151.7520, -32.9190)))
                statement.execute(address("G1", null, "12", 151.7501, -32.92015))
                statement.execute(address("G2", "3", "12", 151.7501, -32.92016))
                statement.execute(address("G3", null, "9", 151.7501, -32.92017))
                statement.execute("INSERT INTO address_lot (gnaf_pid, lot_id, method) VALUES ('G1', '1001', 'legal_parcel_id'), ('G2', '1001', 'legal_parcel_id'), ('G3', '1001', 'point_in_polygon')")
            }
        }
    }

    private fun withLots(block: suspend (PostgisLotDirectory, PostgisLotTiles) -> Unit) {
        val dataSource = PostgisSuburbTiles.connect(DatabaseConfig(url, user, password))
        try { runBlocking { block(PostgisLotDirectory(dataSource), PostgisLotTiles(dataSource)) } } finally { dataSource.close() }
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

    @Test
    fun `a street-level tile holds the lots under it in the lot layer`() = withLots { _, tiles ->
        val (x, y) = tileOf(151.7501, -32.9201, TileContract.LOTS_MIN_ZOOM)
        val counts = featureCounts(tiles.tile(TileContract.LOTS_MIN_ZOOM, x, y))
        assertEquals(mapOf(TileContract.LOT_POLYGON_LAYER to 4), counts)
    }

    @Test
    fun `a tile over empty ground is empty`() = withLots { _, tiles ->
        assertEquals(0, tiles.tile(TileContract.LOTS_MIN_ZOOM, 0, 0).size)
    }

    @Test
    fun `a point finds the smallest lot that covers it`() = withLots { directory, _ ->
        // Lot 1004 surrounds everything; the small lot must win inside it.
        assertEquals("1001", directory.findAt(latitude = -32.92015, longitude = 151.7501)?.lotId)
        assertEquals("1002", directory.findAt(latitude = -32.92015, longitude = 151.7503)?.lotId)
        assertEquals("1004", directory.findAt(latitude = -32.9195, longitude = 151.7495)?.lotId)
        assertNull(directory.findAt(latitude = -33.87, longitude = 151.21))
        assertNull(directory.findAt(latitude = 151.7501, longitude = -32.92015)) // not swapped
    }

    @Test
    fun `a lot is found by id with its suburb, area and formatted addresses in street order`() = withLots { directory, _ ->
        val lot = assertNotNull(directory.find("1001"))
        assertEquals("1//DP100", lot.parcelLabel)
        assertEquals("HAMILTON", lot.suburbName)
        assertEquals("2303", lot.postcode)
        assertTrue(lot.areaM2 in 560.0..680.0, "0.0002 x 0.0003 degrees near -32.92 is about 620 square metres, got ${lot.areaM2}")
        assertEquals(
            listOf("9 MAIN STREET, HAMILTON NSW 2303", "12 MAIN STREET, HAMILTON NSW 2303", "3/12 MAIN STREET, HAMILTON NSW 2303"),
            lot.addresses,
        )
        assertEquals(emptyList(), assertNotNull(directory.find("1002")).addresses)
    }

    @Test
    fun `a strata lot is recognised from its parcel key`() = withLots { directory, _ ->
        assertTrue(LotReportFactory.isStrata(assertNotNull(directory.find("1003")).parcelKey))
        assertTrue(!LotReportFactory.isStrata(assertNotNull(directory.find("1001")).parcelKey))
    }

    @Test
    fun `an unknown or hostile id finds nothing`() = withLots { directory, _ ->
        assertNull(directory.find("424242"))
        assertNull(directory.find("1001' OR '1'='1"))
        assertNull(directory.find(""))
    }
}
