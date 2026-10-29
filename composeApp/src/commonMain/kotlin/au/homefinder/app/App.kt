package au.homefinder.app

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import au.homefinder.app.api.ApiConfig
import au.homefinder.app.api.ApiStatusBadge
import au.homefinder.app.api.KtorHttpGet
import au.homefinder.app.api.SuburbApi
import au.homefinder.app.map.MapConfig
import au.homefinder.app.map.MapInteraction
import au.homefinder.app.map.MapLibreRenderer
import au.homefinder.app.map.MapRenderer
import au.homefinder.app.map.MapView
import au.homefinder.app.map.SuburbTileConfig
import au.homefinder.app.selection.MapUiState
import au.homefinder.app.selection.MapViewModel
import au.homefinder.app.ui.SuburbCardHost
import au.homefinder.shared.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun App(api: ApiConfig? = null, renderer: MapRenderer = MapLibreRenderer) {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val viewModel = remember(api) {
            api?.takeIf { it.apiKey.isNotBlank() }?.let { MapViewModel(SuburbApi(it, KtorHttpGet()), scope) }
        }
        val uiState = (viewModel?.state ?: remember { MutableStateFlow(MapUiState()) }).collectAsState()
        val ui = uiState.value
        // One stable object: the renderers read the selection from it while drawing, so the highlight follows it.
        val interaction = remember(viewModel) {
            MapInteraction(
                selectedSuburbId = { uiState.value.selectedSuburbId },
                onTap = { tap -> viewModel?.onMapTap(tap.latitude, tap.longitude, tap.zoom) },
            )
        }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        PlatformBackHandler(enabled = ui.selectedSuburbId != null) { viewModel?.clear() }

        Box(
            Modifier.fillMaxSize()
                .onPreviewKeyEvent { event ->
                    val clear = clearsSelection(event.type, event.key, hasSelection = ui.selectedSuburbId != null)
                    if (clear) viewModel?.clear()
                    clear
                }
                .focusRequester(focus)
                .focusable(),
        ) {
            SuburbCardHost(ui.card, onClose = { viewModel?.clear() }, onRetry = { viewModel?.retry() }) {
                Box(Modifier.fillMaxSize()) {
                    MapView(Modifier.fillMaxSize(), mapConfig(api), renderer, interaction)
                    if (api != null) ApiStatusBadge(api, Modifier.align(Alignment.TopStart).padding(16.dp))
                    ui.notice?.let { notice ->
                        Surface(
                            Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                        ) { Text(notice, Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                    }
                }
            }
        }
    }
}

/** Escape clears the selection, on key down, and only when there is one (so it is left alone for other uses). */
fun clearsSelection(type: KeyEventType, key: Key, hasSelection: Boolean): Boolean =
    hasSelection && type == KeyEventType.KeyDown && key == Key.Escape

/** The pilot map, with suburb layers when the API and its key are known. */
fun mapConfig(api: ApiConfig?): MapConfig =
    MapConfig.Pilot.copy(suburbs = api?.takeIf { it.apiKey.isNotBlank() }?.let { SuburbTileConfig(it.baseUrl, it.apiKey) })

fun appTitle(): String = AppInfo.NAME
