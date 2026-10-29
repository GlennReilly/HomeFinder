package au.homefinder.app.map.vector

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

enum class GeometryType { UNKNOWN, POINT, LINE, POLYGON }

/**
 * One feature's geometry as parts in tile coordinates: each part is x0,y0,x1,y1,... Points have one coordinate per
 * part, lines one open part per line, polygons one closed ring per part (outer rings and holes in tile order).
 */
class Geometry(val type: GeometryType, val parts: List<FloatArray>)

class VectorFeature(val type: GeometryType, val properties: Map<String, Any>, val geometry: Geometry) {
    fun string(key: String): String? = properties[key] as? String
}

class VectorLayer(val name: String, val extent: Int, val features: List<VectorFeature>)

class VectorTile(val layers: Map<String, VectorLayer>) {
    operator fun get(name: String): VectorLayer? = layers[name]
}

/** Decodes Mapbox Vector Tile (MVT) protobuf bytes, gzip-compressed or not. */
object VectorTileDecoder {
    const val DEFAULT_EXTENT = 4096

    private const val TILE_LAYERS = 3
    private const val LAYER_NAME = 1
    private const val LAYER_FEATURES = 2
    private const val LAYER_KEYS = 3
    private const val LAYER_VALUES = 4
    private const val LAYER_EXTENT = 5
    private const val FEATURE_TAGS = 2
    private const val FEATURE_TYPE = 3
    private const val FEATURE_GEOMETRY = 4
    private const val VALUE_STRING = 1
    private const val VALUE_FLOAT = 2
    private const val VALUE_DOUBLE = 3
    private const val VALUE_INT = 4
    private const val VALUE_UINT = 5
    private const val VALUE_SINT = 6
    private const val VALUE_BOOL = 7
    private const val GZIP_MAGIC_FIRST = 0x1F
    private const val GZIP_MAGIC_SECOND = 0x8B

    fun decode(bytes: ByteArray): VectorTile {
        val reader = ProtoReader(gunzipIfNeeded(bytes))
        val layers = LinkedHashMap<String, VectorLayer>()
        while (reader.hasNext()) {
            if (reader.nextField() == TILE_LAYERS) {
                val layer = decodeLayer(reader.readMessage())
                layers[layer.name] = layer
            } else {
                reader.skip()
            }
        }
        return VectorTile(layers)
    }

    private fun gunzipIfNeeded(bytes: ByteArray): ByteArray {
        val gzipped = bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == GZIP_MAGIC_FIRST && (bytes[1].toInt() and 0xFF) == GZIP_MAGIC_SECOND
        return if (gzipped) GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes
    }

    private class RawFeature(val type: GeometryType, val tags: LongArray, val commands: LongArray)

    private fun decodeLayer(reader: ProtoReader): VectorLayer {
        var name = ""
        var extent = DEFAULT_EXTENT
        val keys = ArrayList<String>()
        val values = ArrayList<Any>()
        val raw = ArrayList<RawFeature>()
        while (reader.hasNext()) {
            when (reader.nextField()) {
                LAYER_NAME -> name = reader.readString()
                LAYER_FEATURES -> raw.add(decodeFeature(reader.readMessage()))
                LAYER_KEYS -> keys.add(reader.readString())
                LAYER_VALUES -> values.add(decodeValue(reader.readMessage()))
                LAYER_EXTENT -> extent = reader.readVarint().toInt()
                else -> reader.skip()
            }
        }
        val features = raw.map { feature ->
            val properties = HashMap<String, Any>()
            for (i in 0 until feature.tags.size / 2) {
                val key = keys.getOrNull(feature.tags[i * 2].toInt())
                val value = values.getOrNull(feature.tags[i * 2 + 1].toInt())
                if (key != null && value != null) properties[key] = value
            }
            VectorFeature(feature.type, properties, decodeGeometry(feature.type, feature.commands))
        }
        return VectorLayer(name, extent, features)
    }

    private fun decodeFeature(reader: ProtoReader): RawFeature {
        var type = GeometryType.UNKNOWN
        var tags = LongArray(0)
        var commands = LongArray(0)
        while (reader.hasNext()) {
            when (reader.nextField()) {
                FEATURE_TAGS -> tags = reader.readMessage().readPackedVarints()
                FEATURE_TYPE -> type = GeometryType.entries.getOrElse(reader.readVarint().toInt()) { GeometryType.UNKNOWN }
                FEATURE_GEOMETRY -> commands = reader.readMessage().readPackedVarints()
                else -> reader.skip()
            }
        }
        return RawFeature(type, tags, commands)
    }

    private fun decodeValue(reader: ProtoReader): Any {
        var result: Any = ""
        while (reader.hasNext()) {
            when (reader.nextField()) {
                VALUE_STRING -> result = reader.readString()
                VALUE_FLOAT -> result = reader.readFloat().toDouble()
                VALUE_DOUBLE -> result = reader.readDouble()
                VALUE_INT, VALUE_UINT -> result = reader.readVarint().toDouble()
                VALUE_SINT -> result = zigZag(reader.readVarint()).toDouble()
                VALUE_BOOL -> result = reader.readVarint() != 0L
                else -> reader.skip()
            }
        }
        return result
    }

    private const val MOVE_TO = 1
    private const val LINE_TO = 2
    private const val CLOSE_PATH = 7
    private const val COMMAND_ID_MASK = 0x7
    private const val COMMAND_COUNT_SHIFT = 3

    private fun zigZag(value: Long): Long = (value ushr 1) xor -(value and 1)

    private fun decodeGeometry(type: GeometryType, commands: LongArray): Geometry {
        val parts = ArrayList<FloatArray>()
        var current = ArrayList<Float>()
        var x = 0L
        var y = 0L
        var index = 0
        fun finishPart(closeRing: Boolean) {
            if (current.isNotEmpty()) {
                if (closeRing && type == GeometryType.POLYGON && current.size >= 2) {
                    current.add(current[0])
                    current.add(current[1])
                }
                parts.add(current.toFloatArray())
            }
            current = ArrayList()
        }
        while (index < commands.size) {
            val header = commands[index++].toInt()
            val id = header and COMMAND_ID_MASK
            val count = header ushr COMMAND_COUNT_SHIFT
            when (id) {
                MOVE_TO, LINE_TO -> repeat(count) {
                    x += zigZag(commands[index++])
                    y += zigZag(commands[index++])
                    // A MoveTo starts a new part: a new line, a new ring, or (for points) the next point.
                    if (id == MOVE_TO) finishPart(closeRing = false)
                    current.add(x.toFloat())
                    current.add(y.toFloat())
                }
                CLOSE_PATH -> finishPart(closeRing = true)
                else -> error("Unknown geometry command $id")
            }
        }
        finishPart(closeRing = false)
        return Geometry(type, parts)
    }
}
