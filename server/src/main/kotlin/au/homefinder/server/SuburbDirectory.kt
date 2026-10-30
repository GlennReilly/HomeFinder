package au.homefinder.server

import au.homefinder.shared.api.SuburbRef
import kotlinx.datetime.LocalDate
import java.sql.SQLException
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the database knows about one suburb. [areaKm2] is null if the label data has not been computed. */
data class SuburbRecord(val suburbId: String, val name: String, val postcode: String?, val areaKm2: Double?) {
    fun toRef(): SuburbRef = SuburbRef(suburbId, name, postcode)
}

/** Looks suburbs up. A fake stands in for it in route tests. */
interface SuburbDirectory {
    /** The suburb whose boundary contains the point (the smallest if boundaries overlap), or null. */
    suspend fun findAt(latitude: Double, longitude: Double): SuburbRecord?

    suspend fun find(suburbId: String): SuburbRecord?

    /** The suburb's statistics, or null when none have been built (or the pipeline has not created the table yet). */
    suspend fun stats(suburbId: String): SuburbStats? = null
}

/** Reads the `suburb` table loaded by the pipeline; boundaries are GDA2020 (EPSG:7844), which the point uses too. */
class PostgisSuburbDirectory(private val dataSource: DataSource) : SuburbDirectory {
    override suspend fun findAt(latitude: Double, longitude: Double): SuburbRecord? = query(FIND_AT_SQL) { statement ->
        // ST_MakePoint takes x (longitude) first.
        statement.setDouble(1, longitude)
        statement.setDouble(2, latitude)
    }

    override suspend fun find(suburbId: String): SuburbRecord? = query(FIND_SQL) { it.setString(1, suburbId) }

    override suspend fun stats(suburbId: String): SuburbStats? = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(STATS_SQL).use { statement ->
                    statement.setString(1, suburbId)
                    statement.executeQuery().use { rows -> if (rows.next()) readStats(rows) else null }
                }
            }
        } catch (failure: SQLException) {
            // Before the first sales load the table does not exist; that is "no statistics", not an outage.
            if (failure.sqlState == UNDEFINED_TABLE) null else throw failure
        }
    }

    private fun readStats(rows: java.sql.ResultSet): SuburbStats {
        fun long(column: String): Long? = rows.getLong(column).takeIf { !rows.wasNull() }
        fun double(column: String): Double? = rows.getDouble(column).takeIf { !rows.wasNull() }
        return SuburbStats(
            asOf = rows.getDate("as_of")?.toLocalDate()?.let { LocalDate(it.year, it.monthValue, it.dayOfMonth) },
            houseSales = rows.getInt("house_sales"),
            houseMedianAud = long("house_median_aud"),
            unitSales = rows.getInt("unit_sales"),
            unitMedianAud = long("unit_median_aud"),
            housePrevSales = rows.getInt("house_prev_sales"),
            houseChangePercent = double("house_change_pct"),
            blockLots = rows.getInt("block_lots"),
            blockMedianM2 = double("block_median_m2"),
            blockP10M2 = double("block_p10_m2"),
            blockP90M2 = double("block_p90_m2"),
        )
    }

    private suspend fun query(sql: String, bind: (java.sql.PreparedStatement) -> Unit): SuburbRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                bind(statement)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) return@use null
                    val area = rows.getBigDecimal("area_km2")?.toDouble()
                    SuburbRecord(rows.getString("suburb_id"), rows.getString("name"), rows.getString("postcode"), area)
                }
            }
        }
    }

    private companion object {
        private const val GDA2020_SRID = 7844
        private const val UNDEFINED_TABLE = "42P01"
        private const val COLUMNS = "s.suburb_id, s.name, s.postcode, l.area_km2"

        // ST_Covers includes points exactly on a boundary; the smallest suburb wins where boundaries overlap.
        val FIND_AT_SQL = """
            SELECT $COLUMNS FROM suburb s LEFT JOIN suburb_label l ON l.suburb_id = s.suburb_id
            WHERE ST_Covers(s.geom, ST_SetSRID(ST_MakePoint(?, ?), $GDA2020_SRID))
            ORDER BY ST_Area(s.geom_mga), s.suburb_id LIMIT 1
        """.trimIndent()

        val STATS_SQL = """
            SELECT as_of, house_sales, house_median_aud, unit_sales, unit_median_aud, house_prev_sales, house_change_pct,
                   block_lots, block_median_m2, block_p10_m2, block_p90_m2
            FROM suburb_stats WHERE suburb_id = ?
        """.trimIndent()

        val FIND_SQL = """
            SELECT $COLUMNS FROM suburb s LEFT JOIN suburb_label l ON l.suburb_id = s.suburb_id
            WHERE s.suburb_id = ?
        """.trimIndent()
    }
}
