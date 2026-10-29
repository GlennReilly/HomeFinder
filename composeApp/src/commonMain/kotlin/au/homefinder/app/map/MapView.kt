package au.homefinder.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import au.homefinder.shared.api.TileContract
import androidx.compose.runtime.remember
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.unaryMinus
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.dsl.collator
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.neq
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.rememberVectorTileSource
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/**
 * The default renderer: MapLibre (maplibre-compose) on Android and desktop. It draws its own attribution control
 * (OpenFreeMap, OpenMapTiles, OpenStreetMap), which satisfies the data licences; [MapConfig.attribution] is the text
 * for any renderer that does not, and for the attribution screen (PR 25).
 */
val MapLibreRenderer: MapRenderer = MapRenderer { config, modifier, interaction ->
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(config.styleUrl),
        initialCameraPosition = CameraPosition(
            center = Position(longitude = config.initialCamera.longitude, latitude = config.initialCamera.latitude),
            zoom = config.initialCamera.zoom,
        ),
    ) {
        config.suburbs?.let { SuburbLayers(it, interaction.selectedSuburbId()) }
    }
    val interactions = remember(interaction) {
        MapInteractions(MapInteractions.Standard) {
            callbacks {
                click {
                    onEvent { event ->
                        event.position?.let { interaction.onTap(MapTap(it.latitude, it.longitude, mapState.cameraPosition.zoom)) }
                        ClickResult.Consume
                    }
                }
            }
        }
    }
    MaplibreMap(modifier = modifier, state = mapState, interactions = interactions)
}

/** The single place the rest of the app draws a map. The renderer is swappable (spec: risks table). */
@Composable
fun MapView(
    modifier: Modifier = Modifier,
    config: MapConfig = MapConfig.Pilot,
    renderer: MapRenderer = MapLibreRenderer,
    interaction: MapInteraction = MapInteraction.None,
) {
    renderer.Map(config, modifier, interaction)
}

/**
 * Suburb outlines and names from the API's vector tiles, above the base map. The MapLibre style cannot easily set
 * request headers, so the key travels as a query parameter (ADR 0014).
 */
@Composable
private fun SuburbLayers(suburbs: SuburbTileConfig, selectedSuburbId: String?) {
    val source = rememberVectorTileSource(tiles = listOf(suburbs.tilesUrl(keyInQuery = true))) {
        minZoom = TileContract.SUBURBS_MIN_ZOOM
        maxZoom = TileContract.SUBURBS_MAX_ZOOM
    }
    if (selectedSuburbId != null) {
        // The other suburbs dim slightly and the selected one is tinted, under the outlines.
        val selected = feature.get("suburb_id").asString()
        FillLayer(
            id = "suburb-dim",
            source = source,
            sourceLayer = TileContract.SUBURB_POLYGON_LAYER,
            filter = neq(selected, const(selectedSuburbId), collator()),
            color = const(SuburbStyle.dimColor),
        )
        FillLayer(
            id = "suburb-selected-fill",
            source = source,
            sourceLayer = TileContract.SUBURB_POLYGON_LAYER,
            filter = eq(selected, const(selectedSuburbId), collator()),
            color = const(SuburbStyle.selectedFillColor),
        )
    }
    LineLayer(
        id = "suburb-outline",
        source = source,
        sourceLayer = TileContract.SUBURB_POLYGON_LAYER,
        // The fade is in the colour's alpha, not the layer's opacity parameters (ADR 0015).
        color = interpolate(
            linear(), zoom(),
            SuburbStyle.OUTLINE_FULL_OPACITY_UNTIL_ZOOM to const(SuburbStyle.outlineColor),
            SuburbStyle.OUTLINE_FADED_FROM_ZOOM to const(SuburbStyle.outlineColor.copy(alpha = SuburbStyle.OUTLINE_FADED_OPACITY)),
        ),
        width = const(SuburbStyle.OUTLINE_WIDTH_DP.dp),
    )
    if (selectedSuburbId != null) {
        LineLayer(
            id = "suburb-selected-outline",
            source = source,
            sourceLayer = TileContract.SUBURB_POLYGON_LAYER,
            filter = eq(feature.get("suburb_id").asString(), const(selectedSuburbId), collator()),
            color = const(SuburbStyle.outlineColor),
            width = const(SuburbStyle.SELECTED_OUTLINE_WIDTH_DP.dp),
        )
    }
    SymbolLayer(
        id = "suburb-name",
        source = source,
        sourceLayer = TileContract.SUBURB_LABEL_LAYER,
        maxZoom = (SuburbStyle.LABEL_MAX_ZOOM + 1).toFloat(),
        textField = feature.get("name").asString(),
        // MapLibre's default font stack is not on OpenFreeMap's glyph server; names need one it serves.
        textFont = const(SuburbStyle.LABEL_FONT_STACK),
        textSize = const(SuburbStyle.LABEL_SIZE_SP.sp),
        textColor = const(SuburbStyle.labelColor),
        textHaloColor = const(SuburbStyle.labelHaloColor),
        textHaloWidth = const(1.5.dp),
        // Bigger suburbs are placed first (a lower sort key wins), so they keep their names where names collide.
        sortKey = -feature.get("area_km2").asNumber(),
    )
}
