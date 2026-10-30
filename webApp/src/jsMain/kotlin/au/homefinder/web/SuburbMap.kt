package au.homefinder.web

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.TileContract
import au.homefinder.shared.model.GeoBounds
import au.homefinder.shared.model.SuburbRatingPoint
import kotlinx.browser.window

/** Where the map starts: suburb-level zoom over Newcastle and Lake Macquarie (spec: pilot region). */
object MapDefaults {
    const val STYLE_URL: String = "https://tiles.openfreemap.org/styles/liberty"
    const val CENTRE_LONGITUDE: Double = 151.65
    const val CENTRE_LATITUDE: Double = -33.0
    const val ZOOM: Double = 10.0
    const val CONTAINER_ID: String = "map"

    /** Served next to the page by the build (see webApp/build.gradle.kts). */
    const val WORKER_URL: String = "/maplibre-gl-worker.mjs"

    /** Zoom for an address: street level, where lot outlines show and a tap selects a lot (spec zoom table). */
    const val ADDRESS_ZOOM: Double = 18.0

    /** A suburb never fills a closer view than this, however small it is. */
    const val SUBURB_MAX_ZOOM: Double = 15.0

    /** Matches the card: a side panel (360 px) from 600 px wide, a bottom sheet below (CSS). */
    const val WIDE_MIN_WIDTH_PX: Int = 600
    const val PANEL_WIDTH_PX: Int = 360
    const val SHEET_HEIGHT_PX: Int = 300
    const val MARGIN_PX: Int = 24
    const val SEARCH_BAR_PX: Int = 72
    const val FLY_MILLIS: Int = 1_200
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
    private var pendingSuburb: String? = null
    private var pendingLot: String? = null
    private var pendingRatings: List<SuburbRatingPoint> = emptyList()

    init {
        // maplibreCss is read so webpack keeps the stylesheet import.
        check(jsTypeOf(maplibreCss).isNotEmpty())
        map.addControl(MapLibre.NavigationControl(), "top-left")
        // Sources and layers are added on "load": MapLibre 6 has no tile manager for a source added on "style.load".
        map.on("load") {
            map.addSource(SuburbStyle.SOURCE_ID, SuburbStyle.source(apiOrigin + TileContract.suburbsPath() + "/{z}/{x}/{y}" + TileContract.TILE_SUFFIX))
            SuburbStyle.layers().forEach { map.addLayer(it) }
            map.addSource(LotStyle.SOURCE_ID, LotStyle.source(apiOrigin + TileContract.lotsPath() + "/{z}/{x}/{y}" + TileContract.TILE_SUFFIX))
            LotStyle.layers().forEach { map.addLayer(it) }
            map.addSource(RatingStyle.SOURCE_ID, RatingStyle.emptySource())
            map.addLayer(RatingStyle.layer())
            loaded = true
            showSelection(pendingSuburb, pendingLot)
            showRatings(pendingRatings)
        }
        map.on("click") { event ->
            onTap(event.lngLat.lat as Double, event.lngLat.lng as Double, map.getZoom())
        }
    }

    /** Highlights the selected suburb or lot and dims the rest; null clears each. */
    fun showSelection(suburbId: String?, lotId: String?) {
        pendingSuburb = suburbId
        pendingLot = lotId
        if (!loaded) return
        map.setFilter(LotStyle.DIM_LAYER, LotStyle.dimFilter(lotId))
        map.setFilter(LotStyle.SELECTED_FILL_LAYER, LotStyle.selectedFilter(lotId))
        map.setFilter(LotStyle.SELECTED_OUTLINE_LAYER, LotStyle.selectedFilter(lotId))
        map.setFilter(SuburbStyle.DIM_LAYER, SuburbStyle.dimFilter(suburbId))
        map.setFilter(SuburbStyle.SELECTED_FILL_LAYER, SuburbStyle.selectedFilter(suburbId))
        map.setFilter(SuburbStyle.SELECTED_OUTLINE_LAYER, SuburbStyle.selectedFilter(suburbId))
    }

    /** Draws a star score under each rated suburb's name; an empty list clears them. */
    fun showRatings(points: List<SuburbRatingPoint>) {
        pendingRatings = points
        if (!loaded) return
        map.getSource(RatingStyle.SOURCE_ID).setData(RatingStyle.featureCollection(points))
    }

    /** Keeps the target clear of the search box and of the card that is about to open. */
    private fun padding(): dynamic {
        val wide = window.innerWidth >= MapDefaults.WIDE_MIN_WIDTH_PX
        return jsObject(
            "top" to MapDefaults.SEARCH_BAR_PX + MapDefaults.MARGIN_PX,
            "bottom" to if (wide) MapDefaults.MARGIN_PX else MapDefaults.SHEET_HEIGHT_PX,
            "left" to MapDefaults.MARGIN_PX,
            "right" to if (wide) MapDefaults.PANEL_WIDTH_PX + MapDefaults.MARGIN_PX else MapDefaults.MARGIN_PX,
        )
    }

    /** Fits a suburb's boundary in view. */
    fun fitTo(bounds: GeoBounds) {
        map.fitBounds(
            arrayOf(arrayOf(bounds.west, bounds.south), arrayOf(bounds.east, bounds.north)),
            jsObject("padding" to padding(), "maxZoom" to MapDefaults.SUBURB_MAX_ZOOM, "duration" to MapDefaults.FLY_MILLIS),
        )
    }

    /** Flies to a point at street level. */
    fun flyToAddress(latitude: Double, longitude: Double) {
        map.flyTo(
            jsObject(
                "center" to arrayOf(longitude, latitude), "zoom" to MapDefaults.ADDRESS_ZOOM, "padding" to padding(),
                "duration" to MapDefaults.FLY_MILLIS, "essential" to true,
            ),
        )
    }
}
