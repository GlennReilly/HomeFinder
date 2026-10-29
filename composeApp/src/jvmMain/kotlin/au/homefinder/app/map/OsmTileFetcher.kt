package au.homefinder.app.map

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

/**
 * Downloads OpenStreetMap standard raster tiles. The OSM tile usage policy requires a identifying User-Agent and
 * forbids heavy or bulk use, so this is used only for the on-demand desktop fallback map (ADR 0009).
 */
class OsmTileFetcher : AutoCloseable {
    private val client = HttpClient(CIO) {
        install(UserAgent) { agent = USER_AGENT }
    }

    /** Returns the decoded tile, or null if it could not be fetched or decoded. */
    suspend fun fetch(tile: TileId): ImageBitmap? = try {
        val response = client.get(tile.url(TILE_URL_TEMPLATE))
        if (response.status.isSuccess()) {
            val bytes = response.body<ByteArray>()
            withContext(Dispatchers.Default) { Image.makeFromEncoded(bytes).toComposeImageBitmap() }
        } else {
            null
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        null
    }

    override fun close() = client.close()

    companion object {
        const val TILE_URL_TEMPLATE: String = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        const val USER_AGENT: String = "HomeFinder/0.1 (+https://github.com/GlennReilly/HomeFinder)"
        const val ATTRIBUTION: String = "© OpenStreetMap contributors"
    }
}
