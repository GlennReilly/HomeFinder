package au.homefinder.server

import au.homefinder.shared.model.LotRef
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

        /** A strata plan can carry hundreds of addresses; the card lists the first few. */
        const val MAX_ADDRESSES = 50

        val FIND_AT_SQL = """
            SELECT l.lot_id FROM lot l
            WHERE ST_Covers(l.geom, ST_SetSRID(ST_MakePoint(?, ?), $GDA2020_SRID))
            ORDER BY l.area_m2, l.lot_id LIMIT 1
        """.trimIndent()

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
