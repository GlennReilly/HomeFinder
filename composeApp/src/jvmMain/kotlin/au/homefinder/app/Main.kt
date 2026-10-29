package au.homefinder.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import au.homefinder.app.map.MapLibreRenderer
import au.homefinder.app.map.RasterTileRenderer
import au.homefinder.app.map.vector.VectorTileRenderer
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost

private const val RENDERER_PROPERTY = "homefinder.map.renderer"
private const val MAPLIBRE_RENDERER = "maplibre"
private const val VECTOR_RENDERER = "vector"

/**
 * Desktop draws the raster-tile fallback by default because the MapLibre desktop bridge needs a recent GPU driver
 * (ADR 0009). Pass `-PmapRenderer=vector` for our own vector-tile renderer (ADR 0010) or `-PmapRenderer=maplibre` for
 * the MapLibre vector map on capable hardware.
 */
fun main(): Unit = application {
    val requested = System.getProperty(RENDERER_PROPERTY)
    val useMapLibre = requested == MAPLIBRE_RENDERER
    Window(onCloseRequest = ::exitApplication, title = appTitle()) {
        if (useMapLibre) {
            ProvideMapPresentationHost(host = rememberAwtComposeMapPresentationHost(window)) {
                App(MapLibreRenderer)
            }
        } else {
            App(if (requested == VECTOR_RENDERER) VectorTileRenderer else RasterTileRenderer)
        }
    }
}
