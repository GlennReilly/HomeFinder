package au.homefinder.web

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.TileContract

/** Where the map starts: suburb-level zoom over Newcastle and Lake Macquarie (spec: pilot region). */
object MapDefaults {
    const val STYLE_URL: String = "https://tiles.openfreemap.org/styles/liberty"
    const val CENTRE_LONGITUDE: Double = 151.65
    const val CENTRE_LATITUDE: Double = -33.0
    const val ZOOM: Double = 10.0
    const val CONTAINER_ID: String = "map"

    /** Served next to the page by the build (see webApp/build.gradle.kts). */
    const val WORKER_URL: String = "/maplibre-gl-worker.mjs"
}

/**
 * The MapLibre map: OpenFreeMap base, our suburb tiles above it, taps reported to [onTap]. Tile requests to our own
 * API carry the key as a header (ADR 0019), so the key never appears in a URL.
 */
class SuburbMap(apiOrigin: String, apiKey: String, private val onTap: (latitude: Double, longitude: Double, zoom: Double) -> Unit) {
    private val map = createMap(apiOrigin, apiKey)

    private fun createMap(apiOrigin: String, apiKey: String): MapLibre.Map {
        MapLibre.setWorkerUrl(MapDefaults.WORKER_URL)
        return MapLibre.Map(
            jsObject(
                "container" to MapDefaults.CONTAINER_ID,
                "style" to MapDefaults.STYLE_URL,
                "center" to arrayOf(MapDefaults.CENTRE_LONGITUDE, MapDefaults.CENTRE_LATITUDE),
                "zoom" to MapDefaults.ZOOM,
                "transformRequest" to { url: String, _: String ->
                    if (url.startsWith(apiOrigin + TileContract.TILES_PREFIX)) {
                        jsObject("url" to url, "headers" to jsObject(ApiContract.API_KEY_HEADER to apiKey))
                    } else {
                        undefined
                    }
                },
            ),
        )
    }

    private var loaded = false
    private var pendingSelection: String? = null

    init {
        // maplibreCss is read so webpack keeps the stylesheet import.
        check(jsTypeOf(maplibreCss).isNotEmpty())
        map.addControl(MapLibre.NavigationControl(), "top-left")
        // Sources and layers are added on "load": MapLibre 6 has no tile manager for a source added on "style.load".
        map.on("load") {
            map.addSource(SuburbStyle.SOURCE_ID, SuburbStyle.source(apiOrigin + TileContract.suburbsPath() + "/{z}/{x}/{y}" + TileContract.TILE_SUFFIX))
            SuburbStyle.layers().forEach { map.addLayer(it) }
            loaded = true
            showSelection(pendingSelection)
        }
        map.on("click") { event ->
            onTap(event.lngLat.lat as Double, event.lngLat.lng as Double, map.getZoom())
        }
    }

    /** Highlights the selected suburb and dims the rest; null clears it. */
    fun showSelection(suburbId: String?) {
        pendingSelection = suburbId
        if (!loaded) return
        map.setFilter(SuburbStyle.DIM_LAYER, SuburbStyle.dimFilter(suburbId))
        map.setFilter(SuburbStyle.SELECTED_FILL_LAYER, SuburbStyle.selectedFilter(suburbId))
        map.setFilter(SuburbStyle.SELECTED_OUTLINE_LAYER, SuburbStyle.selectedFilter(suburbId))
    }
}
