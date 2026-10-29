package au.homefinder.app.map.vector

import java.io.ByteArrayOutputStream

/** Test helper: builds protobuf bytes so the decoder can be checked without binary fixtures. */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    private fun varint(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private fun tag(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())

    fun uint(field: Int, value: Long) = apply { tag(field, WireType.VARINT); varint(value) }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun bytes(field: Int, value: ByteArray) = apply {
        tag(field, WireType.LENGTH_DELIMITED)
        varint(value.size.toLong())
        out.write(value)
    }

    fun message(field: Int, build: ProtoWriter.() -> Unit) = bytes(field, ProtoWriter().apply(build).toBytes())

    fun packed(field: Int, values: List<Long>) = message(field) { values.forEach { varint(it) } }

    fun fixed32(field: Int, value: Int) = apply {
        tag(field, WireType.FIXED32)
        repeat(4) { out.write((value ushr (it * 8)) and 0xFF) }
    }

    fun fixed64(field: Int, value: Long) = apply {
        tag(field, WireType.FIXED64)
        repeat(8) { out.write(((value ushr (it * 8)) and 0xFF).toInt()) }
    }

    fun toBytes(): ByteArray = out.toByteArray()

    companion object {
        fun zigZag(value: Int): Long = ((value shl 1) xor (value shr 31)).toLong() and 0xFFFFFFFFL
        fun command(id: Int, count: Int): Long = ((count shl 3) or id).toLong()
    }
}
