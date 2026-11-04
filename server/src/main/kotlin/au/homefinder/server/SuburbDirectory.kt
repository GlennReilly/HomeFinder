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

    /** The suburb's OpenStreetMap roll-up, or null when the pipeline has not produced it (no OSM load, or the table is missing). */
    suspend fun amenity(suburbId: String): SuburbAmenity? = null

    /** The suburb's 2021 Census tenure and dwelling counts, or null when none have been loaded (or the table is missing). */
    suspend fun census(suburbId: String): SuburbCensus? = null

    /** The public schools inside the suburb, or null when no school data has been loaded (no load yet, or the table is missing). */
    suspend fun schools(suburbId: String): SuburbSchoolData? = null

    /** Every suburb's filter values (ADR 0035), or null when no schools data is loaded. */
    suspend fun filterValues(): SuburbFilterData? = null
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

    override suspend fun amenity(suburbId: String): SuburbAmenity? = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(AMENITY_SQL).use { statement ->
                    statement.setString(1, suburbId)
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) return@use null
                        fun double(column: String): Double? = rows.getDouble(column).takeIf { !rows.wasNull() }
                        SuburbAmenity(
                            beachFromCentreM = double("beach_from_centre_m"), beachFromEdgeM = double("beach_from_edge_m"),
                            beachName = rows.getString("beach_name"), suburbAreaM2 = rows.getDouble("suburb_area_m2"),
                            parkAreaM2 = rows.getDouble("park_area_m2"), shoppingCentres = rows.getInt("shopping_centres"),
                            supermarkets = rows.getInt("supermarkets"),
                            osmAsOf = rows.getDate("as_of")?.toLocalDate()?.let { LocalDate(it.year, it.monthValue, it.dayOfMonth) },
                        )
                    }
                }
            }
        } catch (failure: SQLException) {
            // Before the first OSM load the table does not exist; that is "no data", not an outage.
            if (failure.sqlState == UNDEFINED_TABLE) null else throw failure
        }
    }

    override suspend fun census(suburbId: String): SuburbCensus? = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(CENSUS_SQL).use { statement ->
                    statement.setString(1, suburbId)
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) return@use null
                        SuburbCensus(
                            tenureStated = rows.getInt("tenure_stated"), rented = rows.getInt("rented"), houses = rows.getInt("houses"),
                            semis = rows.getInt("semis"), flats = rows.getInt("flats"), otherDwellings = rows.getInt("other_dwellings"),
                        )
                    }
                }
            }
        } catch (failure: SQLException) {
            // Before the first Census load the table does not exist; that is "no data", not an outage.
            if (failure.sqlState == UNDEFINED_TABLE) null else throw failure
        }
    }

    override suspend fun schools(suburbId: String): SuburbSchoolData? = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { connection ->
                val extracted = connection.prepareStatement(SCHOOLS_LOADED_SQL).use { statement ->
                    statement.executeQuery().use { rows ->
                        rows.next()
                        if (rows.getInt("n") == 0) return@use null to false
                        rows.getDate("latest")?.toLocalDate()?.let { LocalDate(it.year, it.monthValue, it.dayOfMonth) } to true
                    }
                }
                if (!extracted.second) return@use null
                val schools = connection.prepareStatement(SCHOOLS_SQL).use { statement ->
                    statement.setString(1, suburbId)
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                fun int(column: String): Int? = rows.getInt(column).takeIf { !rows.wasNull() }
                                add(
                                    SchoolRecord(
                                        code = rows.getInt("school_code"), name = rows.getString("name"), levelLabel = rows.getString("level_label"),
                                        icsea = int("icsea"), enrolments = rows.getBigDecimal("enrolment_fte")?.toInt(), website = rows.getString("website"),
                                    ),
                                )
                            }
                        }
                    }
                }
                SuburbSchoolData(schools, extracted.first)
            }
        } catch (failure: SQLException) {
            // Before the first schools load the table does not exist; that is "no data", not an outage.
            if (failure.sqlState == UNDEFINED_TABLE) null else throw failure
        }
    }

    override suspend fun filterValues(): SuburbFilterData? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            // Each criterion's data set may be missing or empty on its own, so each is looked for separately (ADR 0046).
            val schools = hasRows(connection, "school")
            val amenity = hasRows(connection, "suburb_amenity")
            val census = hasRows(connection, "suburb_census")
            if (!schools && !amenity && !census) return@use null
            val schoolsDate = if (schools) singleDate(connection, "SELECT max(extracted_on) FROM school") else null
            val osmDate = if (amenity && hasRows(connection, "osm_import")) singleDate(connection, OSM_AS_OF_SQL) else null
            connection.prepareStatement(filterValuesSql(schools, amenity, census)).use { statement ->
                statement.executeQuery().use { rows ->
                    fun double(column: String): Double? = rows.getDouble(column).takeIf { !rows.wasNull() }
                    val values = buildList {
                        while (rows.next()) {
                            val icsea = rows.getInt("average_icsea").takeIf { !rows.wasNull() }
                            add(SuburbFilterRow(rows.getString("suburb_id"), icsea, double("beach_km"), double("park_share"), double("rented_share")))
                        }
                    }
                    SuburbFilterData(values, schoolsDate, schoolsLoaded = schools, osmLoaded = amenity, osmAsOf = osmDate, censusLoaded = census)
                }
            }
        }
    }

    /** True when the table exists and has at least one row. Table names are constants of this class, never input. */
    private fun hasRows(connection: java.sql.Connection, table: String): Boolean {
        val exists = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL").use { statement ->
            statement.setString(1, "public.$table")
            statement.executeQuery().use { rows -> rows.next() && rows.getBoolean(1) }
        }
        if (!exists) return false
        return connection.createStatement().use { statement ->
            statement.executeQuery("SELECT EXISTS (SELECT 1 FROM $table)").use { rows -> rows.next() && rows.getBoolean(1) }
        }
    }

    private fun singleDate(connection: java.sql.Connection, sql: String): LocalDate? =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                if (rows.next()) rows.getDate(1)?.toLocalDate()?.let { LocalDate(it.year, it.monthValue, it.dayOfMonth) } else null
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

        val OSM_AS_OF_SQL = "SELECT (COALESCE(osm_base, imported_at) AT TIME ZONE 'UTC')::date FROM osm_import"

        val STATS_SQL = """
            SELECT as_of, house_sales, house_median_aud, unit_sales, unit_median_aud, house_prev_sales, house_change_pct,
                   block_lots, block_median_m2, block_p10_m2, block_p90_m2
            FROM suburb_stats WHERE suburb_id = ?
        """.trimIndent()

        val AMENITY_SQL = """
            SELECT a.*, (SELECT (COALESCE(osm_base, imported_at) AT TIME ZONE 'UTC')::date FROM osm_import) AS as_of
            FROM suburb_amenity a WHERE a.suburb_id = ?
        """.trimIndent()

        val CENSUS_SQL = """
            SELECT tenure_stated, rented, houses, semis, flats, other_dwellings FROM suburb_census WHERE suburb_id = ?
        """.trimIndent()

        val SCHOOLS_LOADED_SQL = "SELECT count(*) AS n, max(extracted_on) AS latest FROM school"

        val SCHOOLS_SQL = """
            SELECT school_code, name, level_label, icsea, enrolment_fte, website FROM school WHERE suburb_id = ? ORDER BY name
        """.trimIndent()

        /** The average is over schools that have an ICSEA, rounded half up like the card's average. */
        /**
         * One row per suburb with every criterion's value; the columns of a data set that is not loaded are NULL (ADR 0046). A suburb with no
         * mapped park has a NULL park share, as the card shows it as unknown rather than 0%.
         */
        fun filterValuesSql(schools: Boolean, amenity: Boolean, census: Boolean): String {
            val icsea = if (schools) "(SELECT round(avg(c.icsea))::int FROM school c WHERE c.suburb_id = s.suburb_id)" else "NULL::int"
            val beach = if (amenity) "a.beach_from_centre_m / 1000.0" else "NULL::float8"
            val park = if (amenity) "CASE WHEN a.park_area_m2 > 0 AND a.suburb_area_m2 > 0 THEN least(a.park_area_m2 / a.suburb_area_m2, 1.0) END" else "NULL::float8"
            val rented = if (census) "CASE WHEN k.tenure_stated > 0 THEN k.rented::float8 / k.tenure_stated END" else "NULL::float8"
            val joins = (if (amenity) " LEFT JOIN suburb_amenity a ON a.suburb_id = s.suburb_id" else "") +
                (if (census) " LEFT JOIN suburb_census k ON k.suburb_id = s.suburb_id" else "")
            return "SELECT s.suburb_id, $icsea AS average_icsea, $beach AS beach_km, $park AS park_share, $rented AS rented_share " +
                "FROM suburb s$joins ORDER BY s.suburb_id"
        }

        val FIND_SQL = """
            SELECT $COLUMNS FROM suburb s LEFT JOIN suburb_label l ON l.suburb_id = s.suburb_id
            WHERE s.suburb_id = ?
        """.trimIndent()
    }
}
