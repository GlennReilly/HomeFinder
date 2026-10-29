package au.homefinder.server

/** Web Mercator tile grid arithmetic used to validate tile coordinates. */
object TileMath {
    fun tilesPerSide(zoom: Int): Long = 1L shl zoom

    fun isValid(zoom: Int, x: Int, y: Int): Boolean =
        zoom in 0..MAX_SUPPORTED_ZOOM && x >= 0 && y >= 0 && x < tilesPerSide(zoom) && y < tilesPerSide(zoom)

    private const val MAX_SUPPORTED_ZOOM = 30
}
