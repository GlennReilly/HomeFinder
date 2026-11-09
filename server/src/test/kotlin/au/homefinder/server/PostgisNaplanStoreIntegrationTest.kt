package au.homefinder.server

import au.homefinder.shared.model.NaplanEntry
import au.homefinder.shared.model.NaplanScore
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the real NAPLAN store against PostGIS; see [PostgisSuburbTilesIntegrationTest] for how to supply a database. */
class PostgisNaplanStoreIntegrationTest {
    private val url = System.getenv("HOMEFINDER_TEST_DB_URL")
    private val user = System.getenv("HOMEFINDER_TEST_DB_USER").orEmpty()
    private val password = System.getenv("HOMEFINDER_TEST_DB_PASSWORD").orEmpty()

    private val square = "ST_Multi(ST_GeomFromText('POLYGON((151.74 -32.93, 151.76 -32.93, 151.76 -32.91, 151.74 -32.91, 151.74 -32.93))', 7844))"

    @BeforeEach
    fun seed() {
        assumeTrue(!url.isNullOrBlank(), "no test database configured")
        execute(
            "TRUNCATE school_naplan, school_catchment, school",
            "INSERT INTO school (school_code, name, level_label, icsea, geom) VALUES " +
                "(1234, 'Hamilton Public School', 'Primary School', 1051, ST_SetSRID(ST_MakePoint(151.75, -32.92), 7844))",
            // A zoned school missing from the school list: it can still be given a figure.
            "INSERT INTO school_catchment (school_code, level, catch_type, name, geom) VALUES (5678, 'HIGH', 'HIGH_COED', 'Newcastle HS', $square)",
        )
    }

    private fun execute(vararg statements: String) = DriverManager.getConnection(url, user, password).use { connection ->
        connection.createStatement().use { statement -> statements.forEach { statement.execute(it) } }
    }

    private fun withStore(block: suspend (PostgisNaplanStore) -> Unit) {
        val dataSource = PostgisSuburbTiles.connectWritable(DatabaseConfig(url, user, password))
        try { runBlocking { block(PostgisNaplanStore(dataSource)) } } finally { dataSource.close() }
    }

    @Test
    fun `a figure is saved, read back and replaced`() = withStore { store ->
        assertEquals(emptyMap(), store.forSchools(listOf("1234")))
        val saved = assertIs<NaplanWrite.Saved>(store.put("1234", NaplanEntry(512)))
        assertEquals(512, saved.naplan.score)
        // Dated today (the database's day may differ from this machine's by the time zone).
        val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(saved.naplan.updatedOn.toString()), java.time.LocalDate.now())
        assertTrue(days in -1L..1L, "updated on ${saved.naplan.updatedOn}")
        assertEquals(NaplanScore(512, saved.naplan.updatedOn), store.forSchools(listOf("1234"))["1234"])
        store.put("1234", NaplanEntry(530))
        assertEquals(NaplanScore(530, saved.naplan.updatedOn), store.forSchools(listOf("1234", "5678", "x"))["1234"])
        assertEquals(1L, count("SELECT count(*) FROM school_naplan"))
    }

    @Test
    fun `a school known only from the catchments can have a figure, an unknown code cannot`() = withStore { store ->
        assertIs<NaplanWrite.Saved>(store.put("5678", NaplanEntry(600)))
        assertEquals(NaplanWrite.UnknownSchool, store.put("4242", NaplanEntry(600)))
        assertEquals(NaplanWrite.UnknownSchool, store.put("not-a-code", NaplanEntry(600)))
        assertEquals(listOf("5678"), store.all().map { it.schoolCode })
    }

    @Test
    fun `a figure is removed, and removing again or a hostile code is fine`() = withStore { store ->
        store.put("1234", NaplanEntry(512))
        store.put("5678", NaplanEntry(600))
        store.delete("1234' OR '1'='1")
        assertEquals(2, store.all().size)
        store.delete("1234")
        assertNull(store.forSchools(listOf("1234"))["1234"])
        store.delete("1234")
        assertEquals(listOf("5678"), store.all().map { it.schoolCode })
    }

    @Test
    fun `figures survive reloading the schools`() = withStore { store ->
        store.put("1234", NaplanEntry(512))
        // What load-schools.sh does: replace the school table's contents.
        execute("TRUNCATE school")
        assertEquals(512, store.forSchools(listOf("1234"))["1234"]?.score)
    }

    @Test
    fun `a difference below the national average is saved and read back as negative`() = withStore { store ->
        assertIs<NaplanWrite.Saved>(store.put("1234", NaplanEntry(-41)))
        assertEquals(-41, store.forSchools(listOf("1234"))["1234"]?.score)
        store.put("5678", NaplanEntry(-1000))
        assertEquals(listOf(-41, -1000), store.all().map { it.naplan.score })
    }

    @Test
    fun `a figure saves without a year, the year column stays empty, and a year left from before is not read or overwritten (ADR 0073)`() = withStore { store ->
        store.put("1234", NaplanEntry(-41))
        assertEquals(1L, count("SELECT count(*) FROM school_naplan WHERE school_code = 1234 AND year IS NULL"))
        execute("UPDATE school_naplan SET year = 2024 WHERE school_code = 1234")
        store.put("1234", NaplanEntry(30))
        assertEquals(1L, count("SELECT count(*) FROM school_naplan WHERE school_code = 1234 AND year = 2024"), "the column is kept and not written")
        store.put("1234", NaplanEntry(-41))
        assertEquals(NaplanScore(-41, store.all().first().naplan.updatedOn), store.forSchools(listOf("1234"))["1234"])
    }

    @Test
    fun `a note left from before is neither read nor overwritten`() = withStore { store ->
        execute("INSERT INTO school_naplan (school_code, score, year, note) VALUES (1234, 512, 2024, 'old note')")
        assertEquals(NaplanScore(512, store.forSchools(listOf("1234"))["1234"]?.updatedOn), store.forSchools(listOf("1234"))["1234"])
        store.put("1234", NaplanEntry(-41))
        assertEquals(-41, store.forSchools(listOf("1234"))["1234"]?.score)
        assertEquals(1L, count("SELECT count(*) FROM school_naplan WHERE note = 'old note'"), "the column is kept and not written")
    }

    @Test
    fun `the table refuses figures out of range`() {
        assertFailsWith<java.sql.SQLException> { execute("INSERT INTO school_naplan (school_code, score, year) VALUES (1, 0, 2007)") }
        assertFailsWith<java.sql.SQLException> { execute("INSERT INTO school_naplan (school_code, score, year) VALUES (1, 1001, 2025)") }
        assertFailsWith<java.sql.SQLException> { execute("INSERT INTO school_naplan (school_code, score, year) VALUES (1, -1001, 2025)") }
        assertFailsWith<java.sql.SQLException> { execute("INSERT INTO school_naplan (school_code, score, year) VALUES (1, 500, 2007)") }
    }

    @Test
    fun `before the table exists reads find nothing and writes fail`() = withStore { store ->
        execute("ALTER TABLE school_naplan RENAME TO school_naplan_hidden")
        try {
            assertEquals(emptyMap(), store.forSchools(listOf("1234")))
            assertEquals(emptyList(), store.all())
            assertFailsWith<java.sql.SQLException> { store.put("1234", NaplanEntry(512)) }
        } finally {
            execute("ALTER TABLE school_naplan_hidden RENAME TO school_naplan")
        }
    }

    private fun count(sql: String): Long = DriverManager.getConnection(url, user, password).use { c ->
        c.createStatement().use { st -> st.executeQuery(sql).use { rs -> rs.next(); rs.getLong(1) } }
    }
}
