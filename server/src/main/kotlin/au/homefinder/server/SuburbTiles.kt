package au.homefinder.server

import au.homefinder.shared.api.TileContract
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where suburb tiles come from. A fake stands in for it in route tests. */
interface SuburbTileSource {
    /** The Mapbox vector tile bytes for a tile, empty if no suburb touches it. */
    suspend fun tile(zoom: Int, x: Int, y: Int): ByteArray

    /** Whether the database behind the tiles answers. */
    suspend fun isHealthy(): Boolean
}

/**
 * Serves suburb tiles from tables the pipeline precomputes (`refresh_suburb_tile_data()`): boundary polygons already
 * simplified to about one pixel for the tile's zoom, and one label point per suburb, which falls in exactly one tile
 * so a name is never drawn twice. The tile is assembled with PostGIS `ST_AsMVT`; geometry is served in Web Mercator.
 */
class PostgisSuburbTiles(private val dataSource: DataSource) : SuburbTileSource {
    override suspend fun tile(zoom: Int, x: Int, y: Int): ByteArray = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(TILE_SQL).use { statement ->
                var i = 1
                statement.setInt(i++, zoom); statement.setInt(i++, x); statement.setInt(i++, y)     // the tile envelope
                statement.setInt(i++, EXTENT); statement.setInt(i++, BUFFER)                         // polygon clipping
                statement.setInt(i++, zoom)                                                          // the zoom's simplified geometry
                statement.setInt(i++, EXTENT)                                                        // label clipping (no buffer)
                statement.setInt(i++, EXTENT); statement.setInt(i++, EXTENT)                         // ST_AsMVT extents
                statement.executeQuery().use { rows -> if (rows.next()) rows.getBytes(1) ?: ByteArray(0) else ByteArray(0) }
            }
        }
    }

    override suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        runCatching { dataSource.connection.use { it.createStatement().use { s -> s.execute("SELECT 1") } } }.isSuccess
    }

    companion object {
        const val EXTENT: Int = 4096
        const val BUFFER: Int = 64
        private const val POOL_SIZE = 4
        private const val CONNECTION_TIMEOUT_MILLIS = 3_000L

        fun connect(config: DatabaseConfig): HikariDataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                maximumPoolSize = POOL_SIZE
                connectionTimeout = CONNECTION_TIMEOUT_MILLIS
                isReadOnly = true
                poolName = "homefinder-tiles"
            },
        )

        const val POLYGON_LAYER: String = TileContract.SUBURB_POLYGON_LAYER
        const val LABEL_LAYER: String = TileContract.SUBURB_LABEL_LAYER

        val TILE_SQL: String = """
            WITH bounds AS (SELECT ST_TileEnvelope(?, ?, ?) AS geom),
            shapes AS (
                SELECT s.suburb_id, s.name, s.postcode, ST_AsMVTGeom(g.geom, b.geom, ?, ?, true) AS geom
                FROM suburb_tile_geometry g
                JOIN suburb s ON s.suburb_id = g.suburb_id, bounds b
                WHERE g.zoom = ? AND g.geom && b.geom
            ),
            labels AS (
                SELECT s.suburb_id, s.name, s.postcode, l.area_km2, ST_AsMVTGeom(l.geom, b.geom, ?, 0, true) AS geom
                FROM suburb_label l
                JOIN suburb s ON s.suburb_id = l.suburb_id, bounds b
                WHERE l.geom && b.geom
            )
            SELECT COALESCE((SELECT ST_AsMVT(shapes.*, '$POLYGON_LAYER', ?, 'geom') FROM shapes WHERE geom IS NOT NULL), ''::bytea)
                || COALESCE((SELECT ST_AsMVT(labels.*, '$LABEL_LAYER', ?, 'geom') FROM labels WHERE geom IS NOT NULL), ''::bytea)
        """.trimIndent()
    }
}
