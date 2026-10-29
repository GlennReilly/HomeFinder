package au.homefinder.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import au.homefinder.shared.AppInfo

@Composable
fun App() {
    MaterialTheme {
        Text(appTitle())
    }
}

fun appTitle(): String = AppInfo.NAME
