package au.homefinder.drainage

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes a minimal float32 GeoTIFF like the Geoscience Australia tiles: one uncompressed strip, ModelPixelScale and a ModelTiepoint that
 * ties raster (0, 0) to the tile's north-western corner, in degrees.
 */
object GeoTiffFixtures {
    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4
    private const val TYPE_DOUBLE = 12
    private const val ENTRY_BYTES = 12
    private const val HEADER_BYTES = 8
    private const val TIFF_MAGIC = 42
    private const val SAMPLE_FORMAT_FLOAT = 3
    private const val BITS_PER_FLOAT = 32
    private const val BYTES_PER_FLOAT = 4
    private const val SCALE_VALUES = 3
    private const val TIEPOINT_VALUES = 6

    fun write(file: Path, columns: Int, rows: Int, westDeg: Double, northDeg: Double, pixelDegX: Double, pixelDegY: Double, heights: FloatArray, keepPixelBytes: Int = Int.MAX_VALUE) {
        require(heights.size == columns * rows)
        val entryCount = 12
        val ifdBytes = 2 + entryCount * ENTRY_BYTES + 4
        val scaleOffset = HEADER_BYTES + ifdBytes
        val tiepointOffset = scaleOffset + SCALE_VALUES * 8
        val pixelOffset = tiepointOffset + TIEPOINT_VALUES * 8
        val pixelBytes = heights.size * BYTES_PER_FLOAT
        val out = ByteBuffer.allocate(pixelOffset + pixelBytes).order(ByteOrder.LITTLE_ENDIAN)
        out.put('I'.code.toByte()).put('I'.code.toByte()).putShort(TIFF_MAGIC.toShort()).putInt(HEADER_BYTES)
        out.putShort(entryCount.toShort())
        fun short(tag: Int, value: Int) { out.putShort(tag.toShort()).putShort(TYPE_SHORT.toShort()).putInt(1).putShort(value.toShort()).putShort(0) }
        fun long(tag: Int, value: Int) { out.putShort(tag.toShort()).putShort(TYPE_LONG.toShort()).putInt(1).putInt(value) }
        fun doubles(tag: Int, count: Int, offset: Int) { out.putShort(tag.toShort()).putShort(TYPE_DOUBLE.toShort()).putInt(count).putInt(offset) }
        long(256, columns)
        long(257, rows)
        short(258, BITS_PER_FLOAT)
        short(259, 1)
        short(262, 1)
        long(273, pixelOffset)
        short(277, 1)
        long(278, rows)
        long(279, pixelBytes)
        short(339, SAMPLE_FORMAT_FLOAT)
        doubles(33550, SCALE_VALUES, scaleOffset)
        doubles(33922, TIEPOINT_VALUES, tiepointOffset)
        out.putInt(0)
        doubleArrayOf(pixelDegX, pixelDegY, 0.0).forEach { out.putDouble(it) }
        doubleArrayOf(0.0, 0.0, 0.0, westDeg, northDeg, 0.0).forEach { out.putDouble(it) }
        heights.forEach { out.putFloat(it) }
        val bytes = out.array()
        Files.write(file, if (keepPixelBytes < pixelBytes) bytes.copyOf(pixelOffset + keepPixelBytes) else bytes)
    }
}
