package au.homefinder.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import au.homefinder.app.api.ApiConfig
import au.homefinder.app.map.MapLibreRenderer
import au.homefinder.app.map.RasterTileRenderer
import au.homefinder.app.map.vector.VectorTileRenderer
import au.homefinder.shared.api.ApiContract
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost

private const val RENDERER_PROPERTY = "homefinder.map.renderer"
private const val MAPLIBRE_RENDERER = "maplibre"
private const val VECTOR_RENDERER = "vector"
private const val API_URL_VARIABLE = "HOMEFINDER_API_URL"
private const val API_KEY_VARIABLE = "HOMEFINDER_API_KEY"

/** The desktop app reaches the API at HOMEFINDER_API_URL (default: this machine) with HOMEFINDER_API_KEY. */
fun desktopApiConfig(env: Map<String, String> = System.getenv()): ApiConfig = ApiConfig(
    baseUrl = env[API_URL_VARIABLE]?.takeIf { it.isNotBlank() } ?: "http://localhost:${ApiContract.DEFAULT_PORT}",
    apiKey = env[API_KEY_VARIABLE].orEmpty(),
)

/**
 * Desktop draws the raster-tile fallback by default because the MapLibre desktop bridge needs a recent GPU driver
 * (ADR 0009). Pass `-PmapRenderer=vector` for our own vector-tile renderer (ADR 0010) or `-PmapRenderer=maplibre` for
 * the MapLibre vector map on capable hardware.
 */
fun main(): Unit = application {
    val requested = System.getProperty(RENDERER_PROPERTY)
    val useMapLibre = requested == MAPLIBRE_RENDERER
    val api = desktopApiConfig()
    Window(onCloseRequest = ::exitApplication, title = appTitle()) {
        if (useMapLibre) {
            ProvideMapPresentationHost(host = rememberAwtComposeMapPresentationHost(window)) {
                App(api, MapLibreRenderer)
            }
        } else {
            App(api, if (requested == VECTOR_RENDERER) VectorTileRenderer else RasterTileRenderer)
        }
    }
}
