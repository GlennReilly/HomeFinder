package au.homefinder.app.map.suburb

import androidx.compose.ui.graphics.Path
import au.homefinder.app.map.SuburbTileConfig
import au.homefinder.app.map.TileId
import au.homefinder.app.map.vector.GeometryType
import au.homefinder.app.map.vector.VectorTile
import au.homefinder.app.map.vector.VectorTileDecoder
import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.TileContract
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A suburb's name at a point in tile coordinates; [areaKm2] decides which names win when they collide. */
class SuburbLabel(val name: String, val x: Float, val y: Float, val areaKm2: Double)

/** A suburb tile ready to draw: every boundary as one outline [Path] in tile coordinates, plus the name points. */
class PreparedSuburbTile(val extent: Int, val outlines: Path?, val labels: List<SuburbLabel>) {
    val isEmpty: Boolean get() = outlines == null && labels.isEmpty()

    companion object {
        val EMPTY = PreparedSuburbTile(VectorTileDecoder.DEFAULT_EXTENT, null, emptyList())
        private const val NAME_KEY = "name"
        private const val AREA_KEY = "area_km2"

        fun from(tile: VectorTile): PreparedSuburbTile {
            var outlines: Path? = null
            for (feature in tile[TileContract.SUBURB_POLYGON_LAYER]?.features.orEmpty()) {
                if (feature.type != GeometryType.POLYGON) continue
                val path = outlines ?: Path().also { outlines = it }
                for (ring in feature.geometry.parts) {
                    if (ring.size < MIN_RING_VALUES) continue
                    path.moveTo(ring[0], ring[1])
                    var i = 2
                    while (i < ring.size) {
                        path.lineTo(ring[i], ring[i + 1])
                        i += 2
                    }
                    path.close()
                }
            }
            val labels = tile[TileContract.SUBURB_LABEL_LAYER]?.features.orEmpty().mapNotNull { feature ->
                val name = feature.string(NAME_KEY) ?: return@mapNotNull null
                val point = feature.geometry.parts.firstOrNull()?.takeIf { it.size >= 2 } ?: return@mapNotNull null
                SuburbLabel(name, point[0], point[1], feature.properties[AREA_KEY] as? Double ?: 0.0)
            }
            val extent = tile.layers.values.firstOrNull()?.extent ?: VectorTileDecoder.DEFAULT_EXTENT
            return PreparedSuburbTile(extent, outlines, labels)
        }

        private const val MIN_RING_VALUES = 6 // three points
    }
}

sealed interface BytesResult {
    data class Response(val status: Int, val body: ByteArray) : BytesResult
    data class Failure(val message: String) : BytesResult
}

/** One GET returning bytes; separate from Ktor so the loader can be tested without a network. */
fun interface BytesGet {
    suspend fun get(url: String, headers: Map<String, String>): BytesResult
}

class KtorBytesGet(private val client: HttpClient = HttpClient(CIO) { install(UserAgent) { agent = USER_AGENT } }) : BytesGet, AutoCloseable {
    override suspend fun get(url: String, headers: Map<String, String>): BytesResult = try {
        val response = client.get(url) { headers.forEach { (name, value) -> header(name, value) } }
        BytesResult.Response(response.status.value, response.bodyAsBytes())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        BytesResult.Failure(failure.message ?: failure::class.simpleName.orEmpty())
    }

    override fun close() = client.close()

    private companion object {
        const val USER_AGENT = "HomeFinder/0.1 (+https://github.com/GlennReilly/HomeFinder)"
    }
}

/** Fetches and decodes suburb tiles from the API. 204 is an empty tile; any failure is null so it can be retried. */
class SuburbTileLoader(private val config: SuburbTileConfig, private val http: BytesGet) {
    suspend fun load(tile: TileId): PreparedSuburbTile? {
        val url = tile.url(config.tilesUrl(keyInQuery = false))
        val response = when (val result = http.get(url, mapOf(ApiContract.API_KEY_HEADER to config.apiKey))) {
            is BytesResult.Failure -> return null
            is BytesResult.Response -> result
        }
        return when (response.status) {
            HTTP_NO_CONTENT -> PreparedSuburbTile.EMPTY
            HTTP_OK -> runCatching { withContext(Dispatchers.Default) { PreparedSuburbTile.from(VectorTileDecoder.decode(response.body)) } }.getOrNull()
            else -> null
        }
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_NO_CONTENT = 204
    }
}
