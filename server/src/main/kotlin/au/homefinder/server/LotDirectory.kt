package au.homefinder.server

import au.homefinder.shared.model.LotRef
import kotlinx.datetime.LocalDate
import java.sql.SQLException
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
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

    /** The DEM-derived height and slope of a lot, or null when no DEM has been loaded (or the table is missing). */
    suspend fun terrain(lotId: String): LotTerrainData? = null
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

        val IMPORT_SQL = "SELECT (COALESCE(osm_base, imported_at) AT TIME ZONE 'UTC')::date AS as_of FROM osm_import"

        /** One row whose load date is NULL when nothing has been loaded; the lot's own columns are NULL when it has no coverage. */
        val TERRAIN_SQL = """
            SELECT (SELECT max(dem_loaded_on) FROM lot_terrain) AS loaded_on, t.elevation_m, t.mean_slope_pct, t.max_slope_pct, t.pixels
            FROM (SELECT 1) one LEFT JOIN lot_terrain t ON t.lot_id = ?
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
