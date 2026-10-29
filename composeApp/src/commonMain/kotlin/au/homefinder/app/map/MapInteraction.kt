package au.homefinder.app.map

/** A tap on the map: where it landed and how far in the map was zoomed. */
data class MapTap(val latitude: Double, val longitude: Double, val zoom: Double)

/**
 * What a renderer reports back and needs from the app. [selectedSuburbId] is read while drawing, so a renderer that
 * reads it inside composition redraws its highlight when the selection changes; [onTap] is called for every tap.
 */
class MapInteraction(
    val selectedSuburbId: () -> String? = { null },
    val onTap: (MapTap) -> Unit = {},
) {
    companion object {
        val None = MapInteraction()
    }
}
