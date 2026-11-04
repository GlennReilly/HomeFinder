package au.homefinder.server

import au.homefinder.shared.model.LotRef
import kotlinx.datetime.LocalDate
import java.sql.SQLException
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

/** What the database knows about one lot; [addresses] are already formatted (see [AddressFormat]). */
data class LotRecord(
    val lotId: String,
    val parcelLabel: String?,
    val parcelKey: String?,
    val suburbId: String,
    val suburbName: String,
    val postcode: String?,
    val areaM2: Double,
    val addresses: List<String>,
) {
    fun toRef(): LotRef = LotRef(lotId, suburbId)
}

/** Looks lots up. A fake stands in for it in route tests. */
interface LotDirectory {
    /** The lot whose boundary contains the point (the smallest if lots overlap), or null. */
    suspend fun findAt(latitude: Double, longitude: Double): LotRecord?

    suspend fun find(lotId: String): LotRecord?

    /** The OpenStreetMap proximity of a lot, or null when the pipeline has not produced it (no import, or the tables are missing). */
    suspend fun proximity(lotId: String): LotProximity? = null

    /** The public schools nearest a lot (nearest first), or null when no school data has been loaded (or the table is missing). */
    suspend fun schools(lotId: String): LotSchoolData? = null

    /** The DEM-derived height and slope of a lot, or null when no DEM has been loaded (or the table is missing). */
    suspend fun terrain(lotId: String): LotTerrainData? = null

    /** The height above drainage of a lot, or null when the pipeline has not produced it (or the table is missing). */
    suspend fun drainage(lotId: String): LotDrainageData? = null
}

/** Reads the `lot`, `suburb`, `address_lot` and `address` tables loaded by the pipeline (ADR 0005). */
class PostgisLotDirectory(private val dataSource: DataSource) : LotDirectory {
    override suspend fun findAt(latitude: Double, longitude: Double): LotRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val lotId = connection.prepareStatement(FIND_AT_SQL).use { statement ->
                // ST_MakePoint takes x (longitude) first.
                statement.setDouble(1, longitude)
                statement.setDouble(2, latitude)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }
            lotId?.let { load(connection, it) }
        }
    }

    override suspend fun find(lotId: String): LotRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { load(it, lotId) }
    }

    override suspend fun proximity(lotId: String): LotProximity? = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { connection ->
                val asOf = connection.prepareStatement(IMPORT_SQL).use { statement ->
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) return@use null to false
                        rows.getDate("as_of")?.toLocalDate()?.let { LocalDate(it.year, it.monthValue, it.dayOfMonth) } to true
                    }
                }
                if (!asOf.second) return@use null
                val nearest = connection.prepareStatement(NEAREST_SQL).use { statement ->
                    statement.setString(1, lotId)
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) add(NearFeature(rows.getString("kind"), rows.getInt("rank"), rows.getString("name"), rows.getDouble("distance_m")))
                        }
                    }
                }
                val parkArea = connection.prepareStatement(AMENITY_SQL).use { statement ->
                    statement.setString(1, lotId)
                    statement.executeQuery().use { rows -> if (rows.next()) rows.getDouble("park_area_radius_m2") else null }
                }
                LotProximity(nearest, parkArea, asOf.first)
            }
        } catch (failure: SQLException) {
            // Before the first OSM load the tables hold nothing or do not exist; that is "no data", not an outage.
            if (failure.sqlState == UNDEFINED_TABLE) null else throw failure
        }
    }

    override suspend fun schools(lotId: String): LotSchoolData? = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(SCHOOLS_NEAR_SQL).use { statement ->
                    statement.setString(1, lotId)
                    statement.executeQuery().use { rows ->
                        val candidates = ArrayList<NearSchool>()
                        var extracted: LocalDate? = null
                        while (rows.next()) {
                            extracted = rows.getDate("extracted_on")?.toLocalDate()?.let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }
                            val enrolment = rows.getDouble("enrolment_fte").takeIf { !rows.wasNull() }?.roundToInt()
                            val icsea = rows.getInt("icsea").takeIf { !rows.wasNull() }
                            candidates += NearSchool(
                                SchoolRecord(rows.getInt("school_code"), rows.getString("name"), rows.getString("level_label"), icsea, enrolment, rows.getString("website")),
                                rows.getDouble("distance_m"),
                            )
                        }
                        // No rows means no school data (or no such lot): the same as not loaded.
                        if (candidates.isEmpty()) null else LotSchoolData(candidates, extracted)
                    }
                }
            }
        } catch (failure: SQLException) {
            // Before the first schools load the table does not exist; that is "no data", not an outage.
            if (failure.sqlState == UNDEFINED_TABLE) null else throw failure
        }
    }

    override suspend fun terrain(lotId: String): LotTerrainData? = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(TERRAIN_SQL).use { statement ->
                    statement.setString(1, lotId)
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) return@use null
                        val loaded = rows.getDate("loaded_on")?.toLocalDate() ?: return@use null
                        fun double(column: String): Double? = rows.getDouble(column).takeIf { !rows.wasNull() }
                        val elevation = double("elevation_m")
                        val terrain = elevation?.let { LotTerrain(it, double("mean_slope_pct"), double("max_slope_pct"), rows.getInt("pixels")) }
                        LotTerrainData(terrain, LocalDate(loaded.year, loaded.monthValue, loaded.dayOfMonth))
                    }
                }
            }
        } catch (failure: SQLException) {
            // Before the first DEM load the table does not exist; that is "no data", not an outage.
            if (failure.sqlState == UNDEFINED_TABLE) null else throw failure
        }
    }

    override suspend fun drainage(lotId: String): LotDrainageData? = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(DRAINAGE_SQL).use { statement ->
                    statement.setString(1, lotId)
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) return@use null
                        val computed = rows.getDate("computed_on")?.toLocalDate() ?: return@use null
                        val height = rows.getDouble("height_above_drainage_m").takeIf { !rows.wasNull() }
                        val catchment = rows.getDouble("stream_catchment_ha").takeIf { !rows.wasNull() }
                        val drainage = if (height != null && catchment != null) LotDrainage(height, catchment) else null
                        LotDrainageData(drainage, LocalDate(computed.year, computed.monthValue, computed.dayOfMonth))
                    }
                }
            }
        } catch (failure: SQLException) {
            // Before the first drainage load the table does not exist; that is "no data", not an outage.
            if (failure.sqlState == UNDEFINED_TABLE) null else throw failure
        }
    }

    private fun load(connection: java.sql.Connection, lotId: String): LotRecord? {
        val lot = connection.prepareStatement(LOT_SQL).use { statement ->
            statement.setString(1, lotId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return null
                LotRecord(
                    lotId = rows.getString("lot_id"),
                    parcelLabel = rows.getString("lot_id_string"),
                    parcelKey = rows.getString("parcel_key"),
                    suburbId = rows.getString("suburb_id"),
                    suburbName = rows.getString("name"),
                    postcode = rows.getString("postcode"),
                    areaM2 = rows.getDouble("area_m2"),
                    addresses = emptyList(),
                )
            }
        }
        val addresses = connection.prepareStatement(ADDRESSES_SQL).use { statement ->
            statement.setString(1, lotId)
            statement.setInt(2, MAX_ADDRESSES)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            AddressFormat.format(
                                flatNumber = rows.getString("flat_number"), numberFirst = rows.getString("number_first"),
                                streetName = rows.getString("street_name"), streetType = rows.getString("street_type"),
                                locality = rows.getString("locality_name"), state = rows.getString("state"), postcode = rows.getString("postcode"),
                            ),
                        )
                    }
                }
            }
        }
        return lot.copy(addresses = addresses)
    }

    private companion object {
        private const val GDA2020_SRID = 7844
        private const val UNDEFINED_TABLE = "42P01"

        /** A strata plan can carry hundreds of addresses; the card lists the first few. */
        const val MAX_ADDRESSES = 50

        val FIND_AT_SQL = """
            SELECT l.lot_id FROM lot l
            WHERE ST_Covers(l.geom, ST_SetSRID(ST_MakePoint(?, ?), $GDA2020_SRID))
            ORDER BY l.area_m2, l.lot_id LIMIT 1
        """.trimIndent()

        /** How many nearby schools are fetched, so a primary and a high school are both found even where one kind is sparse. */
        const val SCHOOL_CANDIDATES: Int = 30

        val IMPORT_SQL = "SELECT (COALESCE(osm_base, imported_at) AT TIME ZONE 'UTC')::date AS as_of FROM osm_import"

        /** One row whose load date is NULL when nothing has been loaded; the lot's own columns are NULL when it has no coverage. */
        val TERRAIN_SQL = """
            SELECT (SELECT max(dem_loaded_on) FROM lot_terrain) AS loaded_on, t.elevation_m, t.mean_slope_pct, t.max_slope_pct, t.pixels
            FROM (SELECT 1) one LEFT JOIN lot_terrain t ON t.lot_id = ?
        """.trimIndent()

        /** One row whose load date is NULL when nothing has been loaded; the lot's own columns are NULL when it has no result. */
        val DRAINAGE_SQL = """
            SELECT (SELECT max(computed_on) FROM lot_drainage) AS computed_on, d.height_above_drainage_m, d.stream_catchment_ha
            FROM (SELECT 1) one LEFT JOIN lot_drainage d ON d.lot_id = ?
        """.trimIndent()

        /** The candidates are the schools nearest the lot by the index (degrees), then put in true metre order from the lot's edge. */
        val SCHOOLS_NEAR_SQL = """
            SELECT (SELECT max(extracted_on) FROM school) AS extracted_on, s.school_code, s.name, s.level_label, s.icsea, s.enrolment_fte, s.website,
                   ST_Distance(ST_Transform(s.geom, 7856), l.geom_mga) AS distance_m
            FROM lot l
            CROSS JOIN LATERAL (SELECT * FROM school ORDER BY geom <-> ST_PointOnSurface(l.geom) LIMIT $SCHOOL_CANDIDATES) s
            WHERE l.lot_id = ?
            ORDER BY distance_m, s.school_code
        """.trimIndent()

        val NEAREST_SQL = "SELECT kind, rank, name, distance_m FROM lot_nearest WHERE lot_id = ? ORDER BY kind, rank"

        val AMENITY_SQL = "SELECT park_area_radius_m2 FROM lot_amenity WHERE lot_id = ?"

        val LOT_SQL = """
            SELECT l.lot_id, l.lot_id_string, l.parcel_key, l.suburb_id, s.name, s.postcode, l.area_m2
            FROM lot l JOIN suburb s ON s.suburb_id = l.suburb_id WHERE l.lot_id = ?
        """.trimIndent()

        val ADDRESSES_SQL = """
            SELECT a.flat_number, a.number_first, a.street_name, a.street_type, a.locality_name, a.state, a.postcode
            FROM address_lot al JOIN address a ON a.gnaf_pid = al.gnaf_pid
            WHERE al.lot_id = ?
            ORDER BY a.street_name, NULLIF(regexp_replace(a.number_first, '\D', '', 'g'), '')::int NULLS LAST, a.number_first, a.flat_number NULLS FIRST
            LIMIT ?
        """.trimIndent()
    }
}
