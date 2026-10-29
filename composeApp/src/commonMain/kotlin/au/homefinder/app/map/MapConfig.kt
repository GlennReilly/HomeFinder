package au.homefinder.app.map

/** Camera position in GDA2020-compatible WGS84 degrees. */
data class MapCamera(val latitude: Double, val longitude: Double, val zoom: Double)

/** Everything a renderer needs to draw the base map. Renderers are swappable behind [MapRenderer]. */
data class MapConfig(
    val styleUrl: String,
    val initialCamera: MapCamera,
    val attribution: String,
    /** Suburb outlines and names drawn over the base map; null when there is no API to serve them. */
    val suburbs: SuburbTileConfig? = null,
) {
    companion object {
        const val OPEN_FREE_MAP_STYLE_URL: String = "https://tiles.openfreemap.org/styles/liberty"
        const val ATTRIBUTION: String = "© OpenMapTiles, © OpenStreetMap contributors"

        // Suburb-level zoom (spec: zoom 5 to 12 selects suburbs) centred on Newcastle and Lake Macquarie.
        private const val PILOT_CENTRE_LATITUDE: Double = -33.0
        private const val PILOT_CENTRE_LONGITUDE: Double = 151.65
        private const val PILOT_ZOOM: Double = 10.0

        val Pilot: MapConfig = MapConfig(
            styleUrl = OPEN_FREE_MAP_STYLE_URL,
            initialCamera = MapCamera(PILOT_CENTRE_LATITUDE, PILOT_CENTRE_LONGITUDE, PILOT_ZOOM),
            attribution = ATTRIBUTION,
        )
    }
}
