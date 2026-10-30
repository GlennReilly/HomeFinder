package au.homefinder.server

import au.homefinder.shared.api.TileContract
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where lot tiles come from. A fake stands in for it in route tests. */
interface LotTileSource {
    /** The Mapbox vector tile bytes for a tile, empty if no lot touches it. */
    suspend fun tile(zoom: Int, x: Int, y: Int): ByteArray
}

/**
 * Serves lot boundaries straight from the `lot` table. At zoom 16 and above a tile holds only a few hundred lots, so
 * the GiST index on the lot geometry answers it directly and nothing needs precomputing (ADR 0022). Geometry is
 * clipped to the tile and served in Web Mercator.
 */
class PostgisLotTiles(private val dataSource: DataSource) : LotTileSource {
    override suspend fun tile(zoom: Int, x: Int, y: Int): ByteArray = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(TILE_SQL).use { statement ->
                statement.setInt(1, zoom); statement.setInt(2, x); statement.setInt(3, y)
                // The order follows the placeholders in the SQL: ST_AsMVT extent, then ST_AsMVTGeom extent and buffer.
                statement.setInt(4, PostgisSuburbTiles.EXTENT); statement.setInt(5, PostgisSuburbTiles.EXTENT)
                statement.setInt(6, PostgisSuburbTiles.BUFFER)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getBytes(1) ?: ByteArray(0) else ByteArray(0) }
            }
        }
    }

    private companion object {
        const val GDA2020_SRID = 7844
        const val LAYER = TileContract.LOT_POLYGON_LAYER

        // The tile envelope is moved to the lots' own CRS so the spatial index is used; lots are moved to the tile's.
        val TILE_SQL = """
            WITH bounds AS (SELECT ST_TileEnvelope(?, ?, ?) AS geom)
            SELECT COALESCE(ST_AsMVT(t.*, '$LAYER', ?, 'geom'), ''::bytea) FROM (
                SELECT l.lot_id, ST_AsMVTGeom(ST_Transform(l.geom, 3857), b.geom, ?, ?, true) AS geom
                FROM lot l, bounds b
                WHERE l.geom && ST_Transform(b.geom, $GDA2020_SRID)
            ) t WHERE t.geom IS NOT NULL
        """.trimIndent()
    }
}
