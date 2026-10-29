package au.homefinder.app.map.vector

import au.homefinder.app.map.vector.ProtoWriter.Companion.command
import au.homefinder.app.map.vector.ProtoWriter.Companion.zigZag
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VectorTileDecoderTest {
    private val moveTo = 1
    private val lineTo = 2
    private val closePath = 7

    private fun feature(type: Int, tags: List<Long>, geometry: List<Long>): ProtoWriter.() -> Unit = {
        packed(2, tags)
        uint(3, type.toLong())
        packed(4, geometry)
    }

    private fun tile(build: ProtoWriter.() -> Unit): ByteArray =
        ProtoWriter().message(3, build).toBytes()

    @Test
    fun `decodes the layer name, extent and string properties`() {
        val bytes = tile {
            string(1, "place")
            message(2, feature(1, listOf(0, 0, 1, 1), listOf(command(moveTo, 1), zigZag(25), zigZag(17))))
            string(3, "class")
            string(3, "name")
            message(4) { string(1, "suburb") }
            message(4) { string(1, "Hamilton") }
            uint(5, 2048)
        }
        val layer = assertNotNull(VectorTileDecoder.decode(bytes)["place"])
        assertEquals(2048, layer.extent)
        val feature = layer.features.single()
        assertEquals("suburb", feature.string("class"))
        assertEquals("Hamilton", feature.string("name"))
    }

    @Test
    fun `extent defaults to 4096`() {
        val bytes = tile { string(1, "water") }
        assertEquals(4096, VectorTileDecoder.decode(bytes)["water"]?.extent)
    }

    @Test
    fun `decodes numeric and boolean property values`() {
        val bytes = tile {
            string(1, "poi")
            message(2, feature(1, listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4), listOf(command(moveTo, 1), 0, 0)))
            listOf("rank", "ele", "score", "delta", "indoor").forEach { string(3, it) }
            message(4) { uint(4, 7) }                       // int_value
            message(4) { fixed32(2, 2.5f.toBits()) }        // float_value
            message(4) { fixed64(3, 9.25.toBits()) }        // double_value
            message(4) { uint(6, zigZag(-3)) }              // sint_value
            message(4) { uint(7, 1) }                       // bool_value
        }
        val props = VectorTileDecoder.decode(bytes)["poi"]!!.features.single().properties
        assertEquals(7.0, props["rank"])
        assertEquals(2.5, props["ele"])
        assertEquals(9.25, props["score"])
        assertEquals(-3.0, props["delta"])
        assertEquals(true, props["indoor"])
    }

    @Test
    fun `points become one part each`() {
        val geometry = listOf(command(moveTo, 2), zigZag(10), zigZag(20), zigZag(5), zigZag(-4))
        val bytes = tile { string(1, "poi"); message(2, feature(1, emptyList(), geometry)) }
        val parts = VectorTileDecoder.decode(bytes)["poi"]!!.features.single().geometry.parts
        assertEquals(2, parts.size)
        assertContentEquals(floatArrayOf(10f, 20f), parts[0])
        assertContentEquals(floatArrayOf(15f, 16f), parts[1])
    }

    @Test
    fun `lines accumulate relative offsets and a second MoveTo starts a new line`() {
        val geometry = listOf(
            command(moveTo, 1), zigZag(2), zigZag(2),
            command(lineTo, 2), zigZag(3), zigZag(0), zigZag(0), zigZag(-1),
            command(moveTo, 1), zigZag(10), zigZag(10),
            command(lineTo, 1), zigZag(1), zigZag(1),
        )
        val bytes = tile { string(1, "transportation"); message(2, feature(2, emptyList(), geometry)) }
        val parts = VectorTileDecoder.decode(bytes)["transportation"]!!.features.single().geometry.parts
        assertEquals(2, parts.size)
        assertContentEquals(floatArrayOf(2f, 2f, 5f, 2f, 5f, 1f), parts[0])
        assertContentEquals(floatArrayOf(15f, 11f, 16f, 12f), parts[1])
    }

    @Test
    fun `polygon rings are closed and holes stay separate parts`() {
        val geometry = listOf(
            command(moveTo, 1), zigZag(0), zigZag(0),
            command(lineTo, 2), zigZag(10), zigZag(0), zigZag(0), zigZag(10),
            command(closePath, 1),
            command(moveTo, 1), zigZag(2), zigZag(-8),
            command(lineTo, 2), zigZag(0), zigZag(2), zigZag(2), zigZag(0),
            command(closePath, 1),
        )
        val bytes = tile { string(1, "building"); message(2, feature(3, emptyList(), geometry)) }
        val geometryOut = VectorTileDecoder.decode(bytes)["building"]!!.features.single().geometry
        assertEquals(GeometryType.POLYGON, geometryOut.type)
        assertEquals(2, geometryOut.parts.size)
        assertContentEquals(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 0f), geometryOut.parts[0])
        assertContentEquals(floatArrayOf(12f, 2f, 12f, 4f, 14f, 4f, 12f, 2f), geometryOut.parts[1])
    }

    @Test
    fun `gzip compressed tiles are decoded`() {
        val plain = tile { string(1, "water") }
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(plain) } }.toByteArray()
        assertNotNull(VectorTileDecoder.decode(gz)["water"])
    }

    @Test
    fun `unknown fields are skipped and missing layers are null`() {
        val bytes = ProtoWriter().uint(1, 99).string(15, "ignored").message(3) { string(1, "water") }.toBytes()
        val decoded = VectorTileDecoder.decode(bytes)
        assertNotNull(decoded["water"])
        assertNull(decoded["landuse"])
    }

    @Test
    fun `a truncated tile fails loudly`() {
        val bytes = tile { string(1, "water") }
        assertFailsWith<IllegalStateException> { VectorTileDecoder.decode(bytes.copyOf(bytes.size - 2)) }
    }

    @Test
    fun `varints and fixed width values are read correctly`() {
        val reader = ProtoReader(ProtoWriter().uint(1, 300).fixed32(2, 7).fixed64(3, 1L shl 40).toBytes())
        assertEquals(1, reader.nextField()); assertEquals(300L, reader.readVarint())
        assertEquals(2, reader.nextField()); assertEquals(7, reader.readFixed32())
        assertEquals(3, reader.nextField()); assertEquals(1L shl 40, reader.readFixed64())
    }
}
