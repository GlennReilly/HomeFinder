package au.homefinder.drainage

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** What a GeoTIFF's first directory says about where its pixels lie: size, north-western corner and pixel size, in degrees. */
data class TiffHeader(val columns: Int, val rows: Int, val westDeg: Double, val northDeg: Double, val pixelDegX: Double, val pixelDegY: Double)

/**
 * Reads only the directory of a classic (not Big) TIFF, so a tile whose pixel data was cut short still opens and can be reported on later.
 * ImageIO checks the strips while reading the header, which would stop the whole run for one truncated download.
 */
object TiffHeaderReader {
    private const val TAG_WIDTH = 256
    private const val TAG_LENGTH = 257
    private const val TAG_PIXEL_SCALE = 33550
    private const val TAG_TIEPOINT = 33922
    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4
    private const val TYPE_DOUBLE = 12
    private const val CLASSIC_TIFF_MAGIC = 42
    private const val HEADER_BYTES = 8
    private const val ENTRY_BYTES = 12
    private const val SCALE_VALUES = 3
    private const val TIEPOINT_VALUES = 6
    private const val BYTES_PER_DOUBLE = 8
    private const val MAX_ENTRIES = 512

    private class Entry(val type: Int, val count: Int, val valueOrOffset: ByteBuffer)

    /** The header, or null when the file is not a classic TIFF with the size and georeferencing tags. Throws [IOException] only for read errors. */
    fun read(file: Path): TiffHeader? = FileChannel.open(file, StandardOpenOption.READ).use { channel ->
        val head = bytesAt(channel, 0, HEADER_BYTES) ?: return null
        val order = when {
            head.get(0) == 'I'.code.toByte() && head.get(1) == 'I'.code.toByte() -> ByteOrder.LITTLE_ENDIAN
            head.get(0) == 'M'.code.toByte() && head.get(1) == 'M'.code.toByte() -> ByteOrder.BIG_ENDIAN
            else -> return null
        }
        head.order(order)
        if (head.getShort(2).toInt() != CLASSIC_TIFF_MAGIC) return null
        val directoryOffset = head.getInt(4).toLong() and 0xFFFF_FFFFL
        val countBytes = bytesAt(channel, directoryOffset, 2)?.order(order) ?: return null
        val entryCount = countBytes.getShort(0).toInt() and 0xFFFF
        if (entryCount == 0 || entryCount > MAX_ENTRIES) return null
        val table = bytesAt(channel, directoryOffset + 2, entryCount * ENTRY_BYTES)?.order(order) ?: return null

        val entries = HashMap<Int, Entry>()
        for (index in 0 until entryCount) {
            val at = index * ENTRY_BYTES
            val value = table.slice(at + 8, 4).order(order)
            entries[table.getShort(at).toInt() and 0xFFFF] = Entry(table.getShort(at + 2).toInt() and 0xFFFF, table.getInt(at + 4), value)
        }
        val columns = integer(entries[TAG_WIDTH]) ?: return null
        val rows = integer(entries[TAG_LENGTH]) ?: return null
        val scale = doubles(channel, entries[TAG_PIXEL_SCALE], SCALE_VALUES, order) ?: return null
        val tiepoint = doubles(channel, entries[TAG_TIEPOINT], TIEPOINT_VALUES, order) ?: return null
        // The tiepoint ties raster position (i, j) to the ground position (x, y); the tiles tie their own corner (0, 0).
        TiffHeader(columns, rows, tiepoint[3] - tiepoint[0] * scale[0], tiepoint[4] + tiepoint[1] * scale[1], scale[0], scale[1])
    }

    private fun integer(entry: Entry?): Int? = when {
        entry == null || entry.count != 1 -> null
        entry.type == TYPE_SHORT -> entry.valueOrOffset.getShort(0).toInt() and 0xFFFF
        entry.type == TYPE_LONG -> entry.valueOrOffset.getInt(0)
        else -> null
    }

    private fun doubles(channel: FileChannel, entry: Entry?, atLeast: Int, order: ByteOrder): DoubleArray? {
        if (entry == null || entry.type != TYPE_DOUBLE || entry.count < atLeast) return null
        val offset = entry.valueOrOffset.getInt(0).toLong() and 0xFFFF_FFFFL
        val raw = bytesAt(channel, offset, entry.count * BYTES_PER_DOUBLE)?.order(order) ?: return null
        return DoubleArray(entry.count) { raw.getDouble(it * BYTES_PER_DOUBLE) }
    }

    private fun bytesAt(channel: FileChannel, position: Long, length: Int): ByteBuffer? {
        if (position < 0 || position + length > channel.size()) return null
        val buffer = ByteBuffer.allocate(length)
        var at = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, at)
            if (read < 0) return null
            at += read
        }
        buffer.flip()
        return buffer
    }
}
