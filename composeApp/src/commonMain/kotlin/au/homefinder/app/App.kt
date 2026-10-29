package au.homefinder.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import au.homefinder.app.map.MapView
import au.homefinder.shared.AppInfo

@Composable
fun App() {
    MaterialTheme {
        MapView(Modifier.fillMaxSize())
    }
}

fun appTitle(): String = AppInfo.NAME
