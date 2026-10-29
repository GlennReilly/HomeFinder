package au.homefinder.app.api

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shows whether the API is reachable and the key works; tap to check again. */
@Composable
fun ApiStatusBadge(config: ApiConfig, modifier: Modifier = Modifier, http: HttpGet = remember { KtorHttpGet() }) {
    var status by remember { mutableStateOf<ApiStatus>(ApiStatus.Checking) }
    var checks by remember { mutableIntStateOf(0) }
    LaunchedEffect(config, checks) {
        status = ApiStatus.Checking
        status = ApiStatusChecker(config, http).check()
    }
    Surface(
        modifier = modifier.clickable { checks++ },
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(status.label(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
