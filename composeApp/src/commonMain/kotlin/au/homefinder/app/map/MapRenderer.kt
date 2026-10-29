package au.homefinder.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The map seam from the spec: a target can swap in another renderer if MapLibre support lags there. */
fun interface MapRenderer {
    @Composable
    fun Map(config: MapConfig, modifier: Modifier, interaction: MapInteraction)
}
