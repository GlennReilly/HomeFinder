package au.homefinder.app.map.vector

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Where to fetch tiles from, read from a TileJSON document. */
data class TileSourceInfo(val template: String, val maxZoom: Int)

object TileJson {
    const val DEFAULT_MAX_ZOOM: Int = 14

    /** The first tile URL template and the deepest zoom level. The template changes with each data release. */
    fun parse(text: String): TileSourceInfo {
        val root = Json.parseToJsonElement(text).jsonObject
        val template = root.getValue("tiles").jsonArray.first().jsonPrimitive.content
        val maxZoom = root["maxzoom"]?.jsonPrimitive?.int ?: DEFAULT_MAX_ZOOM
        return TileSourceInfo(template, maxZoom)
    }
}
