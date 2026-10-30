package au.homefinder.server

import au.homefinder.shared.model.SearchResultKind
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the real search against PostGIS; see [PostgisSuburbTilesIntegrationTest] for how to supply a database. */
class PostgisSearchIntegrationTest {
    private val url = System.getenv("HOMEFINDER_TEST_DB_URL")
    private val user = System.getenv("HOMEFINDER_TEST_DB_USER").orEmpty()
    private val password = System.getenv("HOMEFINDER_TEST_DB_PASSWORD").orEmpty()

    private fun box(x0: Double, y0: Double, x1: Double, y1: Double) =
        "ST_Multi(ST_GeomFromText('POLYGON(($x0 $y0, $x1 $y0, $x1 $y1, $x0 $y1, $x0 $y0))', 7844))"

    private fun suburb(id: String, name: String, postcode: String, geom: String) =
        "INSERT INTO suburb (suburb_id, name, postcode, geom) VALUES ('$id', '$name', '$postcode', $geom)"

    private fun address(pid: String, flat: String?, number: String, street: String, type: String, locality: String, postcode: String, lon: Double, lat: Double) =
        "INSERT INTO address (gnaf_pid, flat_number, number_first, street_name, street_type, locality_name, state, postcode, source_release, geom) " +
            "VALUES ('$pid', ${flat?.let { "'$it'" } ?: "NULL"}, '$number', '$street', '$type', '$locality', 'NSW', '$postcode', 'test', ST_SetSRID(ST_MakePoint($lon, $lat), 7844))"

    @BeforeEach
    fun seed() {
        assumeTrue(!url.isNullOrBlank(), "no test database configured")
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("TRUNCATE suburb, address CASCADE")
                statement.execute(suburb("9001", "HAMILTON", "2303", box(151.74, -32.93, 151.76, -32.91)))
                statement.execute(suburb("9002", "HAMILTON NORTH", "2292", box(151.70, -32.90, 151.72, -32.88)))
                // Two suburbs called BELMONT; the bigger one must come first.
                statement.execute(suburb("9003", "BELMONT", "2280", box(151.65, -33.05, 151.68, -33.02)))
                statement.execute(suburb("9004", "BELMONT", "2999", box(151.00, -33.00, 151.01, -32.99)))
                statement.execute("SELECT refresh_suburb_tile_data()")
                statement.execute(
                    "INSERT INTO lot (lot_id, lot_id_string, parcel_key, plan_label, suburb_id, geom) VALUES ('1001', '1//DP1', '1/1', 'DP1', '9001', ${box(151.7499, -32.9202, 151.7503, -32.9199)})",
                )
                statement.execute(address("G1", null, "12", "MAIN", "STREET", "HAMILTON", "2303", 151.7501, -32.92005))
                statement.execute(address("G2", "3", "12", "MAIN", "STREET", "HAMILTON", "2303", 151.7501, -32.92006))
                statement.execute(address("G3", null, "120", "MAIN", "STREET", "HAMILTON", "2303", 151.7510, -32.9210))
                statement.execute(address("G4", null, "12", "MAINE", "ROAD", "HAMILTON", "2303", 151.7520, -32.9220))
                statement.execute(address("G5", null, "5", "O''CONNELL", "AVENUE", "BELMONT", "2280", 151.66, -33.03))
                statement.execute(address("G6", null, "7", "STOCKTON", "STREET", "HAMILTON NORTH", "2292", 151.71, -32.89))
                statement.execute("INSERT INTO address_lot (gnaf_pid, lot_id, method) VALUES ('G1', '1001', 'legal_parcel_id'), ('G2', '1001', 'legal_parcel_id')")
            }
        }
    }

    private fun withSearch(block: suspend (PostgisSearchIndex) -> Unit) {
        val dataSource = PostgisSuburbTiles.connect(DatabaseConfig(url, user, password))
        try { runBlocking { block(PostgisSearchIndex(dataSource)) } } finally { dataSource.close() }
    }

    private suspend fun PostgisSearchIndex.find(text: String, limit: Int = 8) = search(SearchQuery.parse(text), limit)

    @Test
    fun `a suburb name prefix finds the suburb with its bounds, exact name first`() = withSearch { index ->
        val results = index.find("hamilton")
        assertEquals(listOf("HAMILTON", "HAMILTON NORTH"), results.filter { it.kind == SearchResultKind.SUBURB }.map { it.label })
        val hamilton = results.first()
        assertEquals("Suburb, NSW 2303", hamilton.detail)
        assertEquals(151.74, assertNotNull(hamilton.bounds).west)
        assertEquals(-32.92, hamilton.latitude, 1e-9)
    }

    @Test
    fun `any word of a suburb name matches and a duplicate name lists the bigger suburb first with each postcode`() = withSearch { index ->
        assertEquals(listOf("HAMILTON NORTH"), index.find("north").filter { it.kind == SearchResultKind.SUBURB }.map { it.label })
        val belmonts = index.find("belmont").filter { it.kind == SearchResultKind.SUBURB }
        assertEquals(listOf("9003", "9004"), belmonts.map { it.id })
        assertEquals(listOf("Suburb, NSW 2280", "Suburb, NSW 2999"), belmonts.map { it.detail })
    }

    @Test
    fun `a postcode prefix finds suburbs`() = withSearch { index ->
        assertEquals(listOf("9001"), index.find("2303").filter { it.kind == SearchResultKind.SUBURB }.map { it.id })
        assertEquals(listOf("9002"), index.find("229").filter { it.kind == SearchResultKind.SUBURB }.map { it.id }, "only 2292 starts with 229")
    }

    @Test
    fun `every typed word must begin a word of the address`() = withSearch { index ->
        val labels = index.find("12 main").filter { it.kind == SearchResultKind.ADDRESS }.map { it.label }
        assertTrue("12 MAIN STREET, HAMILTON NSW 2303" in labels, labels.toString())
        assertTrue("3/12 MAIN STREET, HAMILTON NSW 2303" in labels, labels.toString())
        assertTrue("12 MAINE ROAD, HAMILTON NSW 2303" in labels, "a prefix also matches Maine: $labels")
        assertEquals(emptyList(), index.find("12 zzz"))
        assertEquals(emptyList(), index.find("main 99"))
    }

    @Test
    fun `an abbreviated street type matches the full word`() = withSearch { index ->
        val labels = index.find("12 main st").map { it.label }
        assertTrue("12 MAIN STREET, HAMILTON NSW 2303" in labels)
        assertTrue("12 MAINE ROAD, HAMILTON NSW 2303" !in labels, "Maine Road is not a street: $labels")
        assertEquals(listOf("5 O'CONNELL AVENUE, BELMONT NSW 2280"), index.find("5 o'connell ave").map { it.label })
    }

    @Test
    fun `a unit is found as flat slash number and the closest reading ranks first`() = withSearch { index ->
        assertEquals("3/12 MAIN STREET, HAMILTON NSW 2303", index.find("3/12 main st").first().label)
        // "12 main street" reads exactly like G1 (no flat), so it comes before the unit and before number 120.
        assertEquals(listOf("12 MAIN STREET, HAMILTON NSW 2303"), index.find("12 main street").map { it.label }.take(1))
    }

    @Test
    fun `an address carries its point and its lot only when one is linked`() = withSearch { index ->
        val linked = index.find("12 main street").first { it.id == "G1" }
        assertEquals("1001", linked.lotId)
        assertEquals(-32.92005, linked.latitude, 1e-9)
        assertEquals(151.7501, linked.longitude, 1e-9)
        assertNull(index.find("120 main").single().lotId)
    }

    @Test
    fun `suburbs come first and the list never exceeds the limit`() = withSearch { index ->
        val results = index.find("ham", limit = 3)
        assertEquals(3, results.size)
        assertEquals(SearchResultKind.SUBURB, results.first().kind)
        assertEquals(1, index.find("hamilton", limit = 1).size)
    }

    @Test
    fun `hostile text finds nothing and does no harm`() = withSearch { index ->
        assertEquals(emptyList(), index.find("x'; DROP TABLE address; --"))
        assertEquals(emptyList(), index.find("'''"))
        assertTrue(index.find("12 main").isNotEmpty(), "the table is intact")
    }
}
