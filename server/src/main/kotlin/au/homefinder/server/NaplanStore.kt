package au.homefinder.server

import au.homefinder.shared.model.NaplanEntry
import au.homefinder.shared.model.NaplanScore
import au.homefinder.shared.model.SchoolNaplan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

/** What saving a NAPLAN figure came to: kept (as stored, with the day), or refused because no school has that code. */
sealed interface NaplanWrite {
    data class Saved(val naplan: NaplanScore) : NaplanWrite
    data object UnknownSchool : NaplanWrite
}

/** Keeps your NAPLAN figures, one per school code (ADR 0068). A fake stands in for it in route tests. */
interface NaplanStore {
    /** The figures for these school codes; codes with none (or that are not school codes at all) are simply missing from the map. */
    suspend fun forSchools(codes: Collection<String>): Map<String, NaplanScore>

    /** Saves an entry that has passed [au.homefinder.shared.model.NaplanRules.check]. */
    suspend fun put(schoolCode: String, entry: NaplanEntry): NaplanWrite

    /** Removes a figure; removing one that does not exist is not an error. */
    suspend fun delete(schoolCode: String)

    /** Every figure you have typed in, by school code. */
    suspend fun all(): List<SchoolNaplan>
}

/**
 * Reads and writes `school_naplan` (migration V25). It needs a database connection that can write, like the rating store. Before the table
 * exists reads find nothing, so the cards still work until the migration is applied; writes then fail.
 */
class PostgisNaplanStore(private val dataSource: DataSource) : NaplanStore {
    override suspend fun forSchools(codes: Collection<String>): Map<String, NaplanScore> {
        val numbers = codes.mapNotNull(::schoolCodeOf).distinct()
        if (numbers.isEmpty()) return emptyMap()
        return tolerateMissingTable(emptyMap()) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(FOR_SCHOOLS_SQL).use { statement ->
                    statement.setArray(1, connection.createArrayOf("integer", numbers.toTypedArray()))
                    statement.executeQuery().use { rows -> buildMap { while (rows.next()) put(rows.getInt("school_code").toString(), score(rows)) } }
                }
            }
        }
    }

    override suspend fun put(schoolCode: String, entry: NaplanEntry): NaplanWrite {
        val code = schoolCodeOf(schoolCode) ?: return NaplanWrite.UnknownSchool
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(UPSERT_SQL).use { statement ->
                    statement.setInt(1, code)
                    statement.setInt(2, entry.score)
                    statement.setInt(3, code)
                    statement.setInt(4, code)
                    statement.executeQuery().use { rows -> if (rows.next()) NaplanWrite.Saved(score(rows)) else NaplanWrite.UnknownSchool }
                }
            }
        }
    }

    override suspend fun delete(schoolCode: String) {
        val code = schoolCodeOf(schoolCode) ?: return
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(DELETE_SQL).use { statement ->
                    statement.setInt(1, code)
                    statement.executeUpdate()
                }
            }
        }
    }

    override suspend fun all(): List<SchoolNaplan> = tolerateMissingTable(emptyList()) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(ALL_SQL).use { statement ->
                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(SchoolNaplan(rows.getInt("school_code").toString(), score(rows))) } }
            }
        }
    }

    private fun score(rows: ResultSet): NaplanScore {
        val day = rows.getDate("updated_on").toLocalDate()
        return NaplanScore(rows.getInt("score"), LocalDate(day.year, day.monthValue, day.dayOfMonth))
    }

    private suspend fun <T> tolerateMissingTable(whenMissing: T, block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (failure: SQLException) {
            if (failure.sqlState == UNDEFINED_TABLE) whenMissing else throw failure
        }
    }

    companion object {
        private const val UNDEFINED_TABLE = "42P01"
        private const val COLUMNS = "school_code, score, updated_at::date AS updated_on"

        /** A school code is a positive whole number (the `school` table's key); anything else matches no school. */
        fun schoolCodeOf(text: String): Int? = text.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()?.takeIf { it > 0 }

        private const val FOR_SCHOOLS_SQL = "SELECT $COLUMNS FROM school_naplan WHERE school_code = ANY(?)"

        // Only a code the school list or the catchments know is accepted (a zoned school can be missing from the school list).
        private const val UPSERT_SQL = """
            INSERT INTO school_naplan (school_code, score)
            SELECT ?, ?
            WHERE EXISTS (SELECT 1 FROM school WHERE school_code = ?) OR EXISTS (SELECT 1 FROM school_catchment WHERE school_code = ?)
            ON CONFLICT (school_code) DO UPDATE
            SET score = EXCLUDED.score, updated_at = now()
            RETURNING $COLUMNS
        """

        private const val DELETE_SQL = "DELETE FROM school_naplan WHERE school_code = ?"

        private const val ALL_SQL = "SELECT $COLUMNS FROM school_naplan ORDER BY school_code"
    }
}
