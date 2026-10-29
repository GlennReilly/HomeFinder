package au.homefinder.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import au.homefinder.app.map.MapLibreRenderer
import au.homefinder.app.map.RasterTileRenderer
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost

private const val RENDERER_PROPERTY = "homefinder.map.renderer"
private const val MAPLIBRE_RENDERER = "maplibre"

/**
 * Desktop draws the raster-tile fallback by default because the MapLibre desktop bridge needs a recent GPU driver
 * (ADR 0009). Pass `-PmapRenderer=maplibre` to `:composeApp:run` to try the vector map on capable hardware.
 */
fun main(): Unit = application {
    val useMapLibre = System.getProperty(RENDERER_PROPERTY) == MAPLIBRE_RENDERER
    Window(onCloseRequest = ::exitApplication, title = appTitle()) {
        if (useMapLibre) {
            ProvideMapPresentationHost(host = rememberAwtComposeMapPresentationHost(window)) {
                App(MapLibreRenderer)
            }
        } else {
            App(RasterTileRenderer)
        }
    }
}
