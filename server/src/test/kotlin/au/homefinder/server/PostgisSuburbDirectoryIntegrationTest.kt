package au.homefinder.server

import kotlinx.datetime.LocalDate
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

    private fun execute(vararg statements: String) = DriverManager.getConnection(url, user, password).use { connection ->
        connection.createStatement().use { statement -> statements.forEach { statement.execute(it) } }
    }

    @Test
    fun `statistics are read with nulls kept as null`() = withDirectory { directory ->
        execute(
            "TRUNCATE suburb_stats",
            "INSERT INTO suburb_stats (suburb_id, as_of, house_sales, house_median_aud, unit_sales, house_prev_sales, house_change_pct, " +
                "block_lots, block_median_m2, block_p10_m2, block_p90_m2) VALUES ('9001', '2026-06-01', 60, 1050000, 3, 40, 4.2, 300, 560.5, 320.0, 810.25)",
            "INSERT INTO suburb_stats (suburb_id) VALUES ('9002')",
        )
        val hamilton = assertNotNull(directory.stats("9001"))
        assertEquals(LocalDate(2026, 6, 1), hamilton.asOf)
        assertEquals(60, hamilton.houseSales)
        assertEquals(1_050_000L, hamilton.houseMedianAud)
        assertNull(hamilton.unitMedianAud)
        assertEquals(4.2, hamilton.houseChangePercent)
        assertEquals(560.5, hamilton.blockMedianM2)
        assertEquals(810.25, hamilton.blockP90M2)

        val belmont = assertNotNull(directory.stats("9002"))
        assertNull(belmont.asOf)
        assertEquals(0, belmont.houseSales)
        assertNull(belmont.houseMedianAud)
        assertNull(belmont.houseChangePercent)
        assertNull(belmont.blockMedianM2)

        assertNull(directory.stats("424242"))
        assertNull(directory.stats("9001' OR '1'='1"))
    }

    @Test
    fun `a database without the statistics table has no statistics rather than an error`() = withDirectory { directory ->
        execute("ALTER TABLE suburb_stats RENAME TO suburb_stats_hidden")
        try {
            assertNull(directory.stats("9001"))
        } finally {
            execute("ALTER TABLE suburb_stats_hidden RENAME TO suburb_stats")
        }
    }

    @Test
    fun `a suburb's OpenStreetMap roll-up is read with nulls kept and the date of the data`() = withDirectory { directory ->
        execute(
            "TRUNCATE osm_import, suburb_amenity",
            "INSERT INTO osm_import (imported_at, osm_base, features) VALUES ('2026-10-30 08:00+00', '2026-10-29 22:00+00', 7)",
            "INSERT INTO suburb_amenity (suburb_id, beach_from_centre_m, beach_from_edge_m, beach_name, suburb_area_m2, park_area_m2, shopping_centres, supermarkets) " +
                "VALUES ('9001', 1794.5, 860.25, 'Ocean Beach', 4150000, 50170, 1, 2), ('9002', NULL, NULL, NULL, 3000000, 0, 0, 0)",
        )
        val hamilton = assertNotNull(directory.amenity("9001"))
        assertEquals(1794.5, hamilton.beachFromCentreM)
        assertEquals(860.25, hamilton.beachFromEdgeM)
        assertEquals("Ocean Beach", hamilton.beachName)
        assertEquals(1, hamilton.shoppingCentres)
        assertEquals(2, hamilton.supermarkets)
        assertEquals(kotlinx.datetime.LocalDate(2026, 10, 29), hamilton.osmAsOf)
        assertEquals(50170.0 / 4150000.0, assertNotNull(hamilton.parkShare), 1e-9)
        val belmont = assertNotNull(directory.amenity("9002"))
        assertNull(belmont.beachFromCentreM)
        assertNull(belmont.beachName)
        assertEquals(0.0, belmont.parkShare)
        assertNull(directory.amenity("424242"))
        assertNull(directory.amenity("9001' OR '1'='1"))
    }

    @Test
    fun `a database without the roll-up table has no roll-up rather than an error`() = withDirectory { directory ->
        execute("ALTER TABLE suburb_amenity RENAME TO suburb_amenity_hidden")
        try {
            assertNull(directory.amenity("9001"))
        } finally {
            execute("ALTER TABLE suburb_amenity_hidden RENAME TO suburb_amenity")
        }
    }

    @Test
    fun `a suburb's public schools are read with unpublished values as null, and no data means null`() = withDirectory { directory ->
        execute("TRUNCATE school")
        assertNull(directory.schools("9001"), "nothing loaded yet")
        execute(
            "INSERT INTO school (school_code, name, level_label, icsea, enrolment_fte, website, geom, suburb_id, extracted_on) VALUES " +
                "(1, 'Hamilton Public School', 'Primary School', 1051, 412.5, 'https://h.example', ST_SetSRID(ST_MakePoint(151.745, -32.915), 7844), '9001', '2026-10-02'), " +
                "(2, 'Alpha Small School', 'Primary School', NULL, NULL, NULL, ST_SetSRID(ST_MakePoint(151.746, -32.916), 7844), '9001', '2026-10-02'), " +
                "(3, 'Belmont Public School', 'Primary School', 960, 188, NULL, ST_SetSRID(ST_MakePoint(151.665, -33.035), 7844), '9002', '2026-10-02')",
        )
        val hamilton = assertNotNull(directory.schools("9001"))
        assertEquals(listOf("Alpha Small School", "Hamilton Public School"), hamilton.schools.map { it.name }, "by name")
        val big = hamilton.schools.last()
        assertEquals(1051, big.icsea)
        assertEquals(412, big.enrolments)
        assertEquals("https://h.example", big.website)
        assertNull(hamilton.schools.first().icsea)
        assertNull(hamilton.schools.first().enrolments)
        assertEquals(kotlinx.datetime.LocalDate(2026, 10, 2), hamilton.extractedOn)
        assertEquals(listOf("Belmont Public School"), assertNotNull(directory.schools("9002")).schools.map { it.name })
        // Data is loaded but a suburb with none of its own gets an empty list, not "not loaded".
        assertEquals(emptyList(), assertNotNull(directory.schools("9003")).schools)
        assertEquals(emptyList(), assertNotNull(directory.schools("9001' OR '1'='1")).schools)
    }

    @Test
    fun `filter values average each suburb's ICSEA and leave suburbs without one empty`() = withDirectory { directory ->
        execute("TRUNCATE school")
        assertNull(directory.filterValues(), "nothing loaded yet")
        execute(
            "INSERT INTO school (school_code, name, level_label, icsea, geom, suburb_id, extracted_on) VALUES " +
                "(1, 'A', 'Primary School', 1051, ST_SetSRID(ST_MakePoint(151.745, -32.915), 7844), '9001', '2026-10-02'), " +
                "(2, 'B', 'Primary School', 1052, ST_SetSRID(ST_MakePoint(151.746, -32.916), 7844), '9001', '2026-10-02'), " +
                "(3, 'C', 'Primary School', NULL, ST_SetSRID(ST_MakePoint(151.747, -32.917), 7844), '9001', '2026-10-02'), " +
                "(4, 'D', 'Primary School', 960, ST_SetSRID(ST_MakePoint(151.665, -33.035), 7844), '9002', '2026-10-02')",
        )
        val data = assertNotNull(directory.filterValues())
        val byId = data.suburbs.associate { it.suburbId to it.averageIcsea }
        assertEquals(1052, byId["9001"], "1051.5 rounds half up, the unpublished one is ignored")
        assertEquals(960, byId["9002"])
        assertNull(byId["9003"], "a suburb with no school is listed with no value")
        assertEquals(LocalDate(2026, 10, 2), data.icseaAsOf)
    }

    @Test
    fun `a database without the school table has no school data rather than an error`() = withDirectory { directory ->
        execute("ALTER TABLE school RENAME TO school_hidden")
        try {
            assertNull(directory.schools("9001"))
        } finally {
            execute("ALTER TABLE school_hidden RENAME TO school")
        }
    }
}
