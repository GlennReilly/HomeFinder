package au.homefinder.server

import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the real suburb lookups against PostGIS; see [PostgisSuburbTilesIntegrationTest] for how to supply a database. */
class PostgisSuburbDirectoryIntegrationTest {
    private val url = System.getenv("HOMEFINDER_TEST_DB_URL")
    private val user = System.getenv("HOMEFINDER_TEST_DB_USER").orEmpty()
    private val password = System.getenv("HOMEFINDER_TEST_DB_PASSWORD").orEmpty()

    private fun box(id: String, name: String, postcode: String?, x0: Double, y0: Double, x1: Double, y1: Double) =
        "INSERT INTO suburb (suburb_id, name, postcode, geom) VALUES ('$id', '$name', ${postcode?.let { "'$it'" } ?: "NULL"}, " +
            "ST_Multi(ST_GeomFromText('POLYGON(($x0 $y0, $x1 $y0, $x1 $y1, $x0 $y1, $x0 $y0))', 7844)))"

    @BeforeEach
    fun seed() {
        assumeTrue(!url.isNullOrBlank(), "no test database configured")
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("TRUNCATE suburb CASCADE")
                statement.execute(box("9001", "HAMILTON", "2303", 151.74, -32.93, 151.76, -32.91))
                statement.execute(box("9002", "BELMONT", "2280", 151.65, -33.05, 151.68, -33.02))
                statement.execute(box("9003", "LAKE MACQUARIE", null, 151.0, -33.5, 152.0, -32.5)) // a huge suburb around both
                statement.execute("SELECT refresh_suburb_tile_data()")
            }
        }
    }

    private fun withDirectory(block: suspend (SuburbDirectory) -> Unit) {
        val dataSource = PostgisSuburbTiles.connect(DatabaseConfig(url, user, password))
        try { runBlocking { block(PostgisSuburbDirectory(dataSource)) } } finally { dataSource.close() }
    }

    @Test
    fun `a point inside a suburb finds the smallest suburb that covers it`() = withDirectory { directory ->
        // Hamilton lies inside the huge suburb; the smaller one must win.
        assertEquals("HAMILTON", directory.findAt(latitude = -32.92, longitude = 151.75)?.name)
        assertEquals("BELMONT", directory.findAt(latitude = -33.03, longitude = 151.66)?.name)
    }

    @Test
    fun `a point only inside the big suburb finds it`() = withDirectory { directory ->
        assertEquals("LAKE MACQUARIE", directory.findAt(latitude = -32.6, longitude = 151.1)?.name)
    }

    @Test
    fun `a point exactly on a boundary is covered`() = withDirectory { directory ->
        assertEquals("HAMILTON", directory.findAt(latitude = -32.92, longitude = 151.74)?.name)
        assertEquals("HAMILTON", directory.findAt(latitude = -32.91, longitude = 151.74)?.name) // a corner
    }

    @Test
    fun `a point outside every suburb finds nothing`() = withDirectory { directory ->
        assertNull(directory.findAt(latitude = -33.87, longitude = 151.21))
    }

    @Test
    fun `latitude and longitude are not swapped`() = withDirectory { directory ->
        assertNull(directory.findAt(latitude = 151.75, longitude = -32.92))
    }

    @Test
    fun `a suburb is found by id with its area and a missing postcode stays null`() = withDirectory { directory ->
        val hamilton = assertNotNull(directory.find("9001"))
        assertEquals("2303", hamilton.postcode)
        val area = assertNotNull(hamilton.areaKm2)
        assertTrue(area in 4.0..4.3, "0.02 x 0.02 degrees near -32.9 is about 1.87 km x 2.22 km = 4.15 square km, got $area")
        assertNull(directory.find("9003")?.postcode)
    }

    @Test
    fun `an unknown or hostile id finds nothing`() = withDirectory { directory ->
        assertNull(directory.find("424242"))
        assertNull(directory.find("9001' OR '1'='1"))
        assertNull(directory.find(""))
    }
}
