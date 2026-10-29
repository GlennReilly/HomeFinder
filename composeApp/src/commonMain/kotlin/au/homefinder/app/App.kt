package au.homefinder.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import au.homefinder.app.map.MapLibreRenderer
import au.homefinder.app.map.MapRenderer
import au.homefinder.app.map.MapView
import au.homefinder.shared.AppInfo

@Composable
fun App(renderer: MapRenderer = MapLibreRenderer) {
    MaterialTheme {
        MapView(Modifier.fillMaxSize(), renderer = renderer)
    }
}

fun appTitle(): String = AppInfo.NAME
