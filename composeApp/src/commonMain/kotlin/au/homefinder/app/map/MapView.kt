package au.homefinder.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/**
 * The default renderer: MapLibre (maplibre-compose) on Android and desktop. It draws its own attribution control
 * (OpenFreeMap, OpenMapTiles, OpenStreetMap), which satisfies the data licences; [MapConfig.attribution] is the text
 * for any renderer that does not, and for the attribution screen (PR 25).
 */
val MapLibreRenderer: MapRenderer = MapRenderer { config, modifier ->
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(config.styleUrl),
        initialCameraPosition = CameraPosition(
            center = Position(longitude = config.initialCamera.longitude, latitude = config.initialCamera.latitude),
            zoom = config.initialCamera.zoom,
        ),
    )
    MaplibreMap(modifier = modifier, state = mapState)
}

/** The single place the rest of the app draws a map. The renderer is swappable (spec: risks table). */
@Composable
fun MapView(
    modifier: Modifier = Modifier,
    config: MapConfig = MapConfig.Pilot,
    renderer: MapRenderer = MapLibreRenderer,
) {
    renderer.Map(config, modifier)
}
