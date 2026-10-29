package au.homefinder.app.map.vector

import au.homefinder.app.map.TileId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Downloads OpenFreeMap vector tiles and prepares them for drawing. */
class VectorTileFetcher : AutoCloseable {
    private val client = HttpClient(CIO) {
        install(UserAgent) { agent = USER_AGENT }
    }

    /** Reads the TileJSON, which holds the current tile URL template (it changes with each data release). */
    suspend fun fetchSource(): TileSourceInfo? = attempt {
        val response = client.get(TILEJSON_URL)
        if (response.status.isSuccess()) TileJson.parse(response.body<String>()) else null
    }

    suspend fun fetchTile(tile: TileId, template: String): PreparedTile? = attempt {
        val response = client.get(tile.url(template))
        if (response.status.isSuccess()) {
            val bytes = response.body<ByteArray>()
            withContext(Dispatchers.Default) { PreparedTile.from(VectorTileDecoder.decode(bytes)) }
        } else {
            null
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T?): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        null
    }

    override fun close() = client.close()

    companion object {
        const val TILEJSON_URL: String = "https://tiles.openfreemap.org/planet"
        const val USER_AGENT: String = "HomeFinder/0.1 (+https://github.com/GlennReilly/HomeFinder)"
        const val ATTRIBUTION: String = "OpenFreeMap © OpenMapTiles Data from OpenStreetMap"
    }
}
