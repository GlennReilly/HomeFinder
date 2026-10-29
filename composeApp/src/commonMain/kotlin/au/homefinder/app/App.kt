package au.homefinder.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import au.homefinder.app.api.ApiConfig
import au.homefinder.app.api.ApiStatusBadge
import au.homefinder.shared.AppInfo

@Composable
fun App(api: ApiConfig? = null) {
    MaterialTheme {
        Column(Modifier.padding(16.dp)) {
            Text(appTitle())
            if (api != null) ApiStatusBadge(api, Modifier.padding(top = 8.dp))
        }
    }
}

fun appTitle(): String = AppInfo.NAME
