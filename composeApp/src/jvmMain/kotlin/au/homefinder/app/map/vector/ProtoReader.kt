package au.homefinder.app.map.vector

/** Wire types of the protobuf encoding that the vector tile format uses. */
object WireType {
    const val VARINT = 0
    const val FIXED64 = 1
    const val LENGTH_DELIMITED = 2
    const val FIXED32 = 5
}

/** A minimal protobuf reader over a byte range; just enough for Mapbox vector tiles (no dependency needed). */
class ProtoReader(private val bytes: ByteArray, start: Int = 0, private val end: Int = bytes.size) {
    private var position = start

    fun hasNext(): Boolean = position < end

    /** Reads the next field header and returns its field number; the wire type is left in [wireType]. */
    fun nextField(): Int {
        val tag = readVarint().toInt()
        wireType = tag and WIRE_TYPE_MASK
        return tag ushr WIRE_TYPE_BITS
    }

    var wireType: Int = 0
        private set

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            check(position < end) { "Truncated varint" }
            val byte = bytes[position++].toInt()
            result = result or ((byte and VARINT_PAYLOAD_MASK).toLong() shl shift)
            if (byte and VARINT_CONTINUATION_BIT == 0) return result
            shift += VARINT_PAYLOAD_BITS
            check(shift < MAX_VARINT_BITS) { "Varint too long" }
        }
    }

    fun readString(): String {
        val length = readVarint().toInt()
        check(length >= 0 && position + length <= end) { "String runs past the end" }
        return String(bytes, position, length, Charsets.UTF_8).also { position += length }
    }

    /** Returns a reader over the next length-delimited field (a nested message or packed values). */
    fun readMessage(): ProtoReader {
        val length = readVarint().toInt()
        check(length >= 0 && position + length <= end) { "Message runs past the end" }
        return ProtoReader(bytes, position, position + length).also { position += length }
    }

    fun readFixed32(): Int {
        check(position + FIXED32_BYTES <= end) { "Truncated fixed32" }
        var value = 0
        repeat(FIXED32_BYTES) { value = value or ((bytes[position++].toInt() and BYTE_MASK) shl (it * BITS_PER_BYTE)) }
        return value
    }

    fun readFixed64(): Long {
        check(position + FIXED64_BYTES <= end) { "Truncated fixed64" }
        var value = 0L
        repeat(FIXED64_BYTES) { value = value or ((bytes[position++].toLong() and BYTE_MASK.toLong()) shl (it * BITS_PER_BYTE)) }
        return value
    }

    fun readFloat(): Float = Float.fromBits(readFixed32())

    fun readDouble(): Double = Double.fromBits(readFixed64())

    /** Reads every varint in a packed repeated field. */
    fun readPackedVarints(): LongArray {
        val values = ArrayList<Long>()
        while (hasNext()) values.add(readVarint())
        return values.toLongArray()
    }

    fun skip() {
        when (wireType) {
            WireType.VARINT -> readVarint()
            WireType.FIXED64 -> position += FIXED64_BYTES
            WireType.LENGTH_DELIMITED -> readMessage()
            WireType.FIXED32 -> position += FIXED32_BYTES
            else -> error("Unsupported wire type $wireType")
        }
    }

    private companion object {
        const val WIRE_TYPE_BITS = 3
        const val WIRE_TYPE_MASK = 0b111
        const val VARINT_PAYLOAD_BITS = 7
        const val VARINT_PAYLOAD_MASK = 0x7F
        const val VARINT_CONTINUATION_BIT = 0x80
        const val MAX_VARINT_BITS = 70
        const val FIXED32_BYTES = 4
        const val FIXED64_BYTES = 8
        const val BYTE_MASK = 0xFF
        const val BITS_PER_BYTE = 8
    }
}
