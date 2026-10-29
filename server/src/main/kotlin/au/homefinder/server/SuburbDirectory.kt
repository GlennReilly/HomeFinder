package au.homefinder.server

import au.homefinder.shared.api.SuburbRef
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
}

/** Reads the `suburb` table loaded by the pipeline; boundaries are GDA2020 (EPSG:7844), which the point uses too. */
class PostgisSuburbDirectory(private val dataSource: DataSource) : SuburbDirectory {
    override suspend fun findAt(latitude: Double, longitude: Double): SuburbRecord? = query(FIND_AT_SQL) { statement ->
        // ST_MakePoint takes x (longitude) first.
        statement.setDouble(1, longitude)
        statement.setDouble(2, latitude)
    }

    override suspend fun find(suburbId: String): SuburbRecord? = query(FIND_SQL) { it.setString(1, suburbId) }

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
        private const val COLUMNS = "s.suburb_id, s.name, s.postcode, l.area_km2"

        // ST_Covers includes points exactly on a boundary; the smallest suburb wins where boundaries overlap.
        val FIND_AT_SQL = """
            SELECT $COLUMNS FROM suburb s LEFT JOIN suburb_label l ON l.suburb_id = s.suburb_id
            WHERE ST_Covers(s.geom, ST_SetSRID(ST_MakePoint(?, ?), $GDA2020_SRID))
            ORDER BY ST_Area(s.geom_mga), s.suburb_id LIMIT 1
        """.trimIndent()

        val FIND_SQL = """
            SELECT $COLUMNS FROM suburb s LEFT JOIN suburb_label l ON l.suburb_id = s.suburb_id
            WHERE s.suburb_id = ?
        """.trimIndent()
    }
}
