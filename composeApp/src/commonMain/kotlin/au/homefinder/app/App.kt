package au.homefinder.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import au.homefinder.app.api.ApiConfig
import au.homefinder.app.api.ApiStatusBadge
import au.homefinder.app.map.MapView
import au.homefinder.shared.AppInfo

@Composable
fun App(api: ApiConfig? = null) {
    MaterialTheme {
        Box(Modifier.fillMaxSize()) {
            MapView(Modifier.fillMaxSize())
            if (api != null) ApiStatusBadge(api, Modifier.align(Alignment.TopStart).padding(16.dp))
        }
    }
}

fun appTitle(): String = AppInfo.NAME
