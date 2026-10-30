@file:Suppress("unused")

package au.homefinder.web

/**
 * The small part of the MapLibre GL JS API the app uses (ADR 0019). Options, sources, layers and events are plain
 * JavaScript objects ([jsObject]), so only the entry points are declared here.
 */
@JsModule("maplibre-gl")
external object MapLibre {
    class Map(options: dynamic) {
        fun on(type: String, listener: (dynamic) -> Unit)
        fun on(type: String, layerId: String, listener: (dynamic) -> Unit)
        fun addSource(id: String, source: dynamic)
        fun addLayer(layer: dynamic)
        fun setFilter(layerId: String, filter: dynamic)
        fun getZoom(): Double
        fun getSource(id: String): dynamic
        fun fitBounds(bounds: dynamic, options: dynamic = definedExternally)
        fun flyTo(options: dynamic)
        fun addControl(control: dynamic, position: String = definedExternally)
        fun remove()
    }

    class NavigationControl(options: dynamic = definedExternally)

    fun setWorkerUrl(url: String)
}

/** Importing the stylesheet makes webpack bundle it (the map's controls and attribution need it). */
@JsModule("maplibre-gl/dist/maplibre-gl.css")
external val maplibreCss: dynamic

fun jsObject(vararg pairs: Pair<String, Any?>): dynamic {
    val result: dynamic = js("({})")
    for ((key, value) in pairs) result[key] = value
    return result
}

/** A MapLibre style expression or list, which is a JavaScript array. */
fun jsArray(vararg items: dynamic): dynamic = items
