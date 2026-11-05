package au.homefinder.census

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.abs

/** One ring of a polygon: closed (the first point is repeated at the end), as flat x, y pairs. */
class Ring(val xy: DoubleArray) {
    val pointCount: Int get() = xy.size / 2

    /** Positive when the ring runs counter-clockwise (x east, y north). */
    val signedArea: Double
        get() {
            var sum = 0.0
            for (i in 0 until pointCount - 1) sum += xy[2 * i] * xy[2 * i + 3] - xy[2 * i + 2] * xy[2 * i + 1]
            return sum / 2
        }

    /** Even-odd test of whether the point is inside the ring (points on the edge may go either way, which is enough to place a hole). */
    fun contains(x: Double, y: Double): Boolean {
        var inside = false
        var j = pointCount - 1
        for (i in 0 until pointCount) {
            val xi = xy[2 * i]
            val yi = xy[2 * i + 1]
            val xj = xy[2 * j]
            val yj = xy[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}

/**
 * A polygon shape read from a shapefile: its parts as rings. The format does not say which ring is a hole, so [toWkt] works that out
 * (ADR 0056): shapefiles wind outer rings clockwise and holes counter-clockwise, and each hole goes into the smallest outer ring that
 * contains one of its points. A ring that is wound as a hole but lies inside nothing is kept as an outer ring.
 */
class PolygonShape(val rings: List<Ring>) {
    fun toWkt(): String {
        val outers = ArrayList<Ring>()
        val holes = ArrayList<Ring>()
        rings.filter { it.pointCount >= MIN_RING_POINTS && abs(it.signedArea) > 0.0 }.forEach { if (it.signedArea < 0) outers += it else holes += it }
        val holesByOuter = HashMap<Ring, MutableList<Ring>>()
        holes.forEach { hole ->
            val home = outers.filter { it.contains(hole.xy[0], hole.xy[1]) }.minByOrNull { abs(it.signedArea) }
            if (home == null) outers += hole else holesByOuter.getOrPut(home) { ArrayList() } += hole
        }
        if (outers.isEmpty()) return EMPTY_WKT
        return outers.joinToString(",", "MULTIPOLYGON(", ")") { outer ->
            (listOf(outer) + holesByOuter[outer].orEmpty()).joinToString(",", "(", ")") { ring -> ringWkt(ring) }
        }
    }

    private fun ringWkt(ring: Ring): String =
        (0 until ring.pointCount).joinToString(",", "(", ")") { i -> coordinate(ring.xy[2 * i]) + " " + coordinate(ring.xy[2 * i + 1]) }

    private fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.$COORDINATE_DECIMALS" + "f", value)

    companion object {
        const val EMPTY_WKT: String = "MULTIPOLYGON EMPTY"
        private const val MIN_RING_POINTS = 4
        private const val COORDINATE_DECIMALS = 7
    }
}

/** What [Shapefile.read] found: a record is the attribute row beside its shape (null where the record has no shape). */
data class ShapeRecord(val attributes: Map<String, String>, val shape: PolygonShape?)

sealed interface ShapefileResult {
    data class Read(val records: Int) : ShapefileResult
    data class Failed(val reason: String) : ShapefileResult
}

/**
 * Reads a polygon shapefile (`.shp` with its `.dbf` beside it) one record at a time, with no GDAL: the 100-byte header, then for each record
 * the parts and points of a polygon (shape types 5, 15 and 25; the Z and M values of the last two are ignored). The attributes come from the
 * dBase file, trimmed, with records marked deleted left out of both files in step.
 */
object Shapefile {
    private const val FILE_CODE = 9994
    private const val HEADER_BYTES = 100
    private const val RECORD_HEADER_BYTES = 8
    private const val WORD_BYTES = 2
    private const val NULL_SHAPE = 0
    private val POLYGON_TYPES = setOf(5, 15, 25)
    private const val DBF_FIELD_BYTES = 32
    private const val DBF_TERMINATOR = 0x0D
    private const val DBF_DELETED = '*'.code
    private const val DBF_HEADER_BYTES = 32
    private const val DBF_NAME_BYTES = 11
    private const val DBF_LENGTH_OFFSET = 16
    private const val DBF_HEADER_LENGTH_OFFSET = 8
    private const val DBF_RECORD_LENGTH_OFFSET = 10
    private const val BOX_DOUBLES = 4
    private const val DOUBLE_BYTES = 8
    private const val INT_BYTES = 4

    fun read(shp: Path, dbf: Path, onRecord: (ShapeRecord) -> Unit): ShapefileResult = try {
        DataInputStream(BufferedInputStream(Files.newInputStream(shp))).use { shpIn ->
            Files.newInputStream(dbf).buffered().use { dbfIn ->
                val header = ByteArray(HEADER_BYTES)
                shpIn.readFully(header)
                if (ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getInt(0) != FILE_CODE) return ShapefileResult.Failed("$shp is not a shapefile (bad header)")
                val type = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(HEADER_TYPE_OFFSET)
                if (type !in POLYGON_TYPES) return ShapefileResult.Failed("$shp holds shape type $type, not polygons")
                val fields = readDbfHeader(dbfIn) ?: return ShapefileResult.Failed("$dbf is not a dBase file")
                var count = 0
                while (true) {
                    val shape = nextShape(shpIn) ?: break
                    val attributes = nextAttributes(dbfIn, fields) ?: return ShapefileResult.Failed("$dbf has fewer records than $shp")
                    if (attributes === DELETED) continue
                    onRecord(ShapeRecord(attributes, shape.value))
                    count++
                }
                ShapefileResult.Read(count)
            }
        }
    } catch (failure: IOException) {
        ShapefileResult.Failed("Could not read $shp: ${failure.message}")
    }

    private const val HEADER_TYPE_OFFSET = 32

    private class Field(val name: String, val length: Int)

    private class DbfHeader(val fields: List<Field>, val recordLength: Int)

    private class Shape(val value: PolygonShape?)

    private fun readDbfHeader(input: java.io.InputStream): DbfHeader? {
        val head = input.readNBytes(DBF_HEADER_BYTES)
        if (head.size < DBF_HEADER_BYTES) return null
        val buffer = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        val headerLength = buffer.getShort(DBF_HEADER_LENGTH_OFFSET).toInt() and 0xFFFF
        val recordLength = buffer.getShort(DBF_RECORD_LENGTH_OFFSET).toInt() and 0xFFFF
        val fields = ArrayList<Field>()
        var read = DBF_HEADER_BYTES
        while (true) {
            // The field list ends with one terminator byte, not a whole 32-byte descriptor, so look at one byte first.
            val first = input.read()
            read++
            if (first < 0 || first == DBF_TERMINATOR) break
            val raw = byteArrayOf(first.toByte()) + input.readNBytes(DBF_FIELD_BYTES - 1)
            read += raw.size - 1
            if (raw.size < DBF_FIELD_BYTES) break
            fields += Field(String(raw, 0, DBF_NAME_BYTES, Charsets.ISO_8859_1).substringBefore('\u0000'), raw[DBF_LENGTH_OFFSET].toInt() and 0xFF)
        }
        input.skipNBytes((headerLength - read).toLong().coerceAtLeast(0))
        return DbfHeader(fields, recordLength)
    }

    /** The next record's attributes, null at the end of the file, or [DELETED] for a record marked deleted. */
    private fun nextAttributes(input: java.io.InputStream, header: DbfHeader): Map<String, String>? {
        val row = input.readNBytes(header.recordLength)
        if (row.size < header.recordLength) return null
        if ((row[0].toInt() and 0xFF) == DBF_DELETED) return DELETED
        var offset = 1
        val values = LinkedHashMap<String, String>()
        header.fields.forEach { field ->
            values[field.name] = String(row, offset, field.length, Charsets.ISO_8859_1).trim()
            offset += field.length
        }
        return values
    }

    private val DELETED: Map<String, String> = emptyMap()

    /** Null at the end of the file; otherwise the next record's shape (whose value is null for a null shape). */
    private fun nextShape(input: DataInputStream): Shape? {
        val recordHeader = ByteArray(RECORD_HEADER_BYTES)
        try {
            input.readFully(recordHeader)
        } catch (end: EOFException) {
            return null
        }
        val contentBytes = ByteBuffer.wrap(recordHeader).order(ByteOrder.BIG_ENDIAN).getInt(INT_BYTES) * WORD_BYTES
        val content = ByteArray(contentBytes)
        input.readFully(content)
        val buffer = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.getInt(0) == NULL_SHAPE) return Shape(null)
        val partCount = buffer.getInt(INT_BYTES + BOX_DOUBLES * DOUBLE_BYTES)
        val pointCount = buffer.getInt(2 * INT_BYTES + BOX_DOUBLES * DOUBLE_BYTES)
        val partsAt = 3 * INT_BYTES + BOX_DOUBLES * DOUBLE_BYTES
        val pointsAt = partsAt + partCount * INT_BYTES
        val starts = IntArray(partCount) { buffer.getInt(partsAt + it * INT_BYTES) }
        val rings = (0 until partCount).map { part ->
            val from = starts[part]
            val to = if (part + 1 < partCount) starts[part + 1] else pointCount
            val xy = DoubleArray((to - from) * 2)
            for (p in from until to) {
                xy[(p - from) * 2] = buffer.getDouble(pointsAt + p * 2 * DOUBLE_BYTES)
                xy[(p - from) * 2 + 1] = buffer.getDouble(pointsAt + p * 2 * DOUBLE_BYTES + DOUBLE_BYTES)
            }
            Ring(xy)
        }
        return Shape(PolygonShape(rings))
    }
}
