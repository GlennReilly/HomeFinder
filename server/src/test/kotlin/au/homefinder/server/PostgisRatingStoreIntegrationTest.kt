package au.homefinder.server

import au.homefinder.shared.model.HomelyRating
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the real rating store against PostGIS; see [PostgisSuburbTilesIntegrationTest] for how to supply a database. */
class PostgisRatingStoreIntegrationTest {
    private val url = System.getenv("HOMEFINDER_TEST_DB_URL")
    private val user = System.getenv("HOMEFINDER_TEST_DB_USER").orEmpty()
    private val password = System.getenv("HOMEFINDER_TEST_DB_PASSWORD").orEmpty()
    private val day = LocalDate(2026, 10, 30)

    private fun box(x0: Double, y0: Double, x1: Double, y1: Double) =
        "ST_Multi(ST_GeomFromText('POLYGON(($x0 $y0, $x1 $y0, $x1 $y1, $x0 $y1, $x0 $y0))', 7844))"

    @BeforeEach
    fun seed() {
        assumeTrue(!url.isNullOrBlank(), "no test database configured")
        execute(
            "TRUNCATE suburb, suburb_rating CASCADE",
            "INSERT INTO suburb (suburb_id, name, postcode, geom) VALUES ('9001', 'HAMILTON', '2303', ${box(151.74, -32.93, 151.76, -32.91)})",
            "INSERT INTO suburb (suburb_id, name, postcode, geom) VALUES ('9002', 'BELMONT', '2280', ${box(151.65, -33.05, 151.68, -33.02)})",
            "SELECT refresh_suburb_tile_data()",
        )
    }

    private fun execute(vararg statements: String) = DriverManager.getConnection(url, user, password).use { connection ->
        connection.createStatement().use { statement -> statements.forEach { statement.execute(it) } }
    }

    private fun withStore(block: suspend (PostgisRatingStore) -> Unit) {
        val dataSource = PostgisSuburbTiles.connectWritable(DatabaseConfig(url, user, password))
        try { runBlocking { block(PostgisRatingStore(dataSource)) } } finally { dataSource.close() }
    }

    @Test
    fun `a rating is saved, read back and replaced`() = withStore { store ->
        assertNull(store.get("9001"))
        store.put("9001", HomelyRating(2.1, 7), day)
        assertEquals(StoredRating(HomelyRating(2.1, 7), day), store.get("9001"))
        store.put("9001", HomelyRating(3.4, null), LocalDate(2026, 11, 2))
        assertEquals(StoredRating(HomelyRating(3.4, null), LocalDate(2026, 11, 2)), store.get("9001"))
        assertEquals(1L, count("SELECT count(*) FROM suburb_rating"))
        assertNull(store.get("9002"))
    }

    @Test
    fun `a rating is removed, and removing again is fine`() = withStore { store ->
        store.put("9001", HomelyRating(2.1, 7), day)
        store.delete("9001")
        assertNull(store.get("9001"))
        store.delete("9001")
        store.delete("never-rated")
    }

    @Test
    fun `the list holds rated suburbs by name with the point of their label`() = withStore { store ->
        store.put("9002", HomelyRating(4.0, 20), day)
        store.put("9001", HomelyRating(2.1, 7), day)
        val list = store.all()
        assertEquals(listOf("BELMONT", "HAMILTON"), list.map { it.name })
        val hamilton = list.last()
        assertEquals(HomelyRating(2.1, 7), hamilton.rating)
        // The label point lies inside the suburb's box (GDA2020 degrees, not Web Mercator metres).
        assertTrue(hamilton.longitude in 151.74..151.76 && hamilton.latitude in -32.93..-32.91, "${hamilton.latitude}, ${hamilton.longitude}")
    }

    @Test
    fun `ratings survive reloading the suburbs, and a rating for a vanished suburb is left out of the list`() = withStore { store ->
        store.put("9001", HomelyRating(2.1, 7), day)
        store.put("9002", HomelyRating(4.0, 20), day)
        // What load-suburbs.sh does: replace the suburb table's contents.
        execute("TRUNCATE suburb CASCADE")
        execute("INSERT INTO suburb (suburb_id, name, postcode, geom) VALUES ('9001', 'HAMILTON', '2303', ${box(151.74, -32.93, 151.76, -32.91)})", "SELECT refresh_suburb_tile_data()")
        assertEquals(HomelyRating(2.1, 7), store.get("9001")?.rating)
        assertEquals(HomelyRating(4.0, 20), store.get("9002")?.rating, "the rating is kept even while its suburb is missing")
        assertEquals(listOf("9001"), store.all().map { it.suburbId })
    }

    @Test
    fun `a hostile id is just an id`() = withStore { store ->
        store.put("9001", HomelyRating(2.1, 7), day)
        store.delete("9001' OR '1'='1")
        assertNotNull(store.get("9001"))
        assertNull(store.get("9001' OR '1'='1"))
    }

    @Test
    fun `the table refuses scores out of range`() {
        assertFailsWith<java.sql.SQLException> { execute("INSERT INTO suburb_rating (suburb_id, stars, rated_on) VALUES ('x', 5.5, '2026-10-30')") }
        assertFailsWith<java.sql.SQLException> { execute("INSERT INTO suburb_rating (suburb_id, stars, review_count, rated_on) VALUES ('x', 3.0, -1, '2026-10-30')") }
    }

    @Test
    fun `before the table exists reads find nothing and writes fail`() = withStore { store ->
        execute("ALTER TABLE suburb_rating RENAME TO suburb_rating_hidden")
        try {
            assertNull(store.get("9001"))
            assertEquals(emptyList(), store.all())
            assertFailsWith<java.sql.SQLException> { store.put("9001", HomelyRating(2.1, 7), day) }
        } finally {
            execute("ALTER TABLE suburb_rating_hidden RENAME TO suburb_rating")
        }
    }

    private fun count(sql: String): Long = DriverManager.getConnection(url, user, password).use { c ->
        c.createStatement().use { st -> st.executeQuery(sql).use { rs -> rs.next(); rs.getLong(1) } }
    }
}
