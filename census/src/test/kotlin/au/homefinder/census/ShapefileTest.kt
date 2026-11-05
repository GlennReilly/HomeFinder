package au.homefinder.census

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/** Hand-made shapefiles: a .shp with polygon records and a .dbf with attributes. */
class ShapefileTest {
    @TempDir
    lateinit var dir: Path

    private val square = listOf(0.0 to 0.0, 0.0 to 10.0, 10.0 to 10.0, 10.0 to 0.0, 0.0 to 0.0) // clockwise: an outer ring
    private val hole = listOf(2.0 to 2.0, 4.0 to 2.0, 4.0 to 4.0, 2.0 to 4.0, 2.0 to 2.0) // counter-clockwise: a hole
    private val island = listOf(20.0 to 0.0, 20.0 to 5.0, 25.0 to 5.0, 25.0 to 0.0, 20.0 to 0.0)

    private fun shapeRecord(number: Int, parts: List<List<Pair<Double, Double>>>?): ByteArray {
        val content = ByteBuffer.allocate(if (parts == null) 4 else 44 + 4 * parts.size + 16 * parts.sumOf { it.size }).order(ByteOrder.LITTLE_ENDIAN)
        if (parts == null) {
            content.putInt(0)
        } else {
            content.putInt(5)
            repeat(4) { content.putDouble(0.0) }
            content.putInt(parts.size).putInt(parts.sumOf { it.size })
            var start = 0
            parts.forEach { content.putInt(start); start += it.size }
            parts.flatten().forEach { content.putDouble(it.first).putDouble(it.second) }
        }
        val header = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(number).putInt(content.capacity() / 2)
        return header.array() + content.array()
    }

    private fun shp(vararg records: List<List<Pair<Double, Double>>>?, type: Int = 5): Path {
        val header = ByteBuffer.allocate(100)
        header.order(ByteOrder.BIG_ENDIAN).putInt(9994)
        header.position(32)
        header.order(ByteOrder.LITTLE_ENDIAN).putInt(type)
        val out = ByteArrayOutputStream()
        out.write(header.array())
        records.forEachIndexed { i, parts -> out.write(shapeRecord(i + 1, parts)) }
        return dir.resolve("t.shp").also { Files.write(it, out.toByteArray()) }
    }

    private fun dbf(vararg rows: List<String>, deleted: Set<Int> = emptySet()): Path {
        val fields = listOf("USE_ID" to 8, "USE_DESC" to 20)
        val headerLength = 32 + 32 * fields.size + 1
        val recordLength = 1 + fields.sumOf { it.second }
        val out = ByteArrayOutputStream()
        out.write(ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).put(3).put(126).put(11).put(1).putInt(rows.size).putShort(headerLength.toShort()).putShort(recordLength.toShort()).array())
        fields.forEach { (name, length) ->
            out.write(name.toByteArray().copyOf(11)); out.write('C'.code); out.write(ByteArray(4)); out.write(length); out.write(0); out.write(ByteArray(14))
        }
        out.write(0x0D)
        rows.forEachIndexed { i, row ->
            out.write(if (i in deleted) '*'.code else ' '.code)
            row.forEachIndexed { c, value -> out.write(value.padEnd(fields[c].second).toByteArray()) }
        }
        return dir.resolve("t.dbf").also { Files.write(it, out.toByteArray()) }
    }

    private fun readAll(shp: Path, dbf: Path): Pair<ShapefileResult, List<ShapeRecord>> {
        val records = ArrayList<ShapeRecord>()
        return Shapefile.read(shp, dbf) { records += it } to records
    }

    @Test
    fun `reads attributes and a polygon with a hole, putting the hole inside its outer ring`() {
        val (result, records) = readAll(shp(listOf(square, hole)), dbf(listOf("2060", "Grafton PS")))
        assertEquals(ShapefileResult.Read(1), result)
        assertEquals(mapOf("USE_ID" to "2060", "USE_DESC" to "Grafton PS"), records.single().attributes)
        val wkt = records.single().shape!!.toWkt()
        assertEquals(
            "MULTIPOLYGON(((0.0000000 0.0000000,0.0000000 10.0000000,10.0000000 10.0000000,10.0000000 0.0000000,0.0000000 0.0000000)," +
                "(2.0000000 2.0000000,4.0000000 2.0000000,4.0000000 4.0000000,2.0000000 4.0000000,2.0000000 2.0000000)))",
            wkt,
        )
    }

    @Test
    fun `two separate outer rings become two polygons, and each hole goes with the ring that contains it`() {
        val shape = PolygonShape(listOf(Ring(doubles(island)), Ring(doubles(square)), Ring(doubles(hole))))
        val wkt = shape.toWkt()
        assertTrue(wkt.startsWith("MULTIPOLYGON((("), wkt)
        assertEquals(2, Regex("""\)\),\(\(""").findAll(wkt).count() + 1, "two polygons: $wkt")
        // The hole's ring sits with the square (the second polygon), not the island.
        assertTrue(wkt.indexOf("2.0000000 2.0000000") > wkt.indexOf("10.0000000 10.0000000"), wkt)
    }

    @Test
    fun `a hole-wound ring inside nothing is kept as a polygon of its own`() {
        val wkt = PolygonShape(listOf(Ring(doubles(hole)))).toWkt()
        assertTrue(wkt.startsWith("MULTIPOLYGON((("), wkt)
    }

    @Test
    fun `a null shape reads as no shape, and a record marked deleted is left out of both files`() {
        val (result, records) = readAll(
            shp(listOf(square), null, listOf(island)),
            dbf(listOf("1", "One"), listOf("2", "Two"), listOf("3", "Three"), deleted = setOf(2)),
        )
        assertEquals(ShapefileResult.Read(2), result)
        assertEquals(listOf("One", "Two"), records.map { it.attributes["USE_DESC"] })
        assertNull(records[1].shape)
    }

    @Test
    fun `a file that is not a shapefile, or not polygons, or short of attribute rows, is refused with a reason`() {
        val notShp = dir.resolve("bad.shp").also { Files.write(it, ByteArray(100)) }
        assertIs<ShapefileResult.Failed>(Shapefile.read(notShp, dbf(listOf("1", "One"))) { })
        val points = shp(listOf(square), type = 1)
        assertTrue((Shapefile.read(points, dbf(listOf("1", "One"))) { } as ShapefileResult.Failed).reason.contains("not polygons"))
        val twoShapes = shp(listOf(square), listOf(island))
        assertTrue((Shapefile.read(twoShapes, dbf(listOf("1", "One"))) { } as ShapefileResult.Failed).reason.contains("fewer records"))
    }

    @Test
    fun `the year flags become a range`() {
        fun flags(vararg served: String) = (listOf("KINDERGART") + (1..12).map { "YEAR$it" }).associateWith { if (it in served) "Y" else "N" }
        assertEquals("K-6", Catchments.years(flags("KINDERGART", "YEAR1", "YEAR2", "YEAR3", "YEAR4", "YEAR5", "YEAR6")))
        assertEquals("K-2", Catchments.years(flags("KINDERGART", "YEAR1", "YEAR2")))
        assertEquals("7-12", Catchments.years(flags("YEAR7", "YEAR8", "YEAR9", "YEAR10", "YEAR11", "YEAR12")))
        assertEquals("K, 3", Catchments.years(flags("KINDERGART", "YEAR3")))
        assertEquals("11", Catchments.years(flags("YEAR11")))
        assertEquals("", Catchments.years(flags()))
    }

    @Test
    fun `catchments are read from the primary and secondary files, with the enrolment year, and the future file is ignored`() {
        fun write(name: String, rows: List<List<String>>) {
            Files.write(dir.resolve("$name.shp"), Files.readAllBytes(shp(*Array(rows.size) { listOf(square) })))
            val fields = listOf("USE_ID" to 8, "USE_DESC" to 20, "CATCH_TYPE" to 12, "ADD_DATE" to 8, "KINDERGART" to 1, "YEAR7" to 1)
            val out = ByteArrayOutputStream()
            out.write(ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).put(3).put(126).put(11).put(1).putInt(rows.size)
                .putShort((32 + 32 * fields.size + 1).toShort()).putShort((1 + fields.sumOf { it.second }).toShort()).array())
            fields.forEach { (n, l) -> out.write(n.toByteArray().copyOf(11)); out.write('C'.code); out.write(ByteArray(4)); out.write(l); out.write(0); out.write(ByteArray(14)) }
            out.write(0x0D)
            rows.forEach { row -> out.write(' '.code); row.forEachIndexed { c, v -> out.write(v.padEnd(fields[c].second).toByteArray()) } }
            Files.write(dir.resolve("$name.dbf"), out.toByteArray())
        }
        write("catchments_primary", listOf(listOf("2060", "Grafton PS", "PRIMARY", "20230516", "Y", "N"), listOf("abc", "Bad", "PRIMARY", "20230516", "Y", "N")))
        write("catchments_secondary", listOf(listOf("8503", "Billabong HS", "HIGH_COED", "20200507", "N", "Y")))
        Files.writeString(dir.resolve("catchment_sf_info.json"), """{"current_enrolment_year":2026}""")
        val read = assertIs<CatchmentsResult.Read>(Catchments.read(dir))
        assertEquals(listOf("2060", "8503"), read.catchments.map { it.schoolCode })
        assertEquals(listOf(CatchmentLevel.PRIMARY, CatchmentLevel.HIGH), read.catchments.map { it.level })
        assertEquals(listOf("K", "7"), read.catchments.map { it.years })
        assertEquals(1, read.skipped, "a non-numeric school code is skipped")
        assertEquals(2026, read.enrolmentYear)
        val csv = CatchmentsCsv.format(read)
        assertEquals(CatchmentsCsv.HEADER, csv.first())
        assertTrue(csv[1].startsWith("PRIMARY,2060,\"PRIMARY\",\"Grafton PS\",20230516,\"K\",2026,\"MULTIPOLYGON((("), csv[1])
    }

    @Test
    fun `a directory without the files is refused with a pointer to the download`() {
        assertTrue((Catchments.read(dir) as CatchmentsResult.Failed).reason.contains("download-catchments.sh"))
    }

    private fun doubles(points: List<Pair<Double, Double>>) = points.flatMap { listOf(it.first, it.second) }.toDoubleArray()
}
