package au.homefinder.app.map.suburb

import au.homefinder.app.map.SuburbTileConfig
import au.homefinder.app.map.TileId
import au.homefinder.app.map.vector.ProtoWriter
import au.homefinder.app.map.vector.ProtoWriter.Companion.command
import au.homefinder.app.map.vector.ProtoWriter.Companion.zigZag
import au.homefinder.app.map.vector.VectorTileDecoder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SuburbTileLoaderTest {
    private val config = SuburbTileConfig("http://host:8080", "secret-key-123456")
    private val tile = TileId(10, 943, 611)

    private val square = listOf(
        command(1, 1), zigZag(100), zigZag(100),
        command(2, 3), zigZag(200), zigZag(0), zigZag(0), zigZag(200), zigZag(-200), zigZag(0),
        command(7, 1),
    )

    /** A tile as the API builds it: a boundary polygon and a label point for HAMILTON. */
    private fun suburbTile(): ByteArray {
        val polygons = ProtoWriter().message(3) {
            string(1, "suburb")
            message(2) { packed(2, listOf(0, 0, 1, 1)); uint(3, 3); packed(4, square) }
            string(3, "name")
            string(3, "postcode")
            message(4) { string(1, "HAMILTON") }
            message(4) { string(1, "2303") }
        }.toBytes()
        val labels = ProtoWriter().message(3) {
            string(1, "suburb_label")
            message(2) { packed(2, listOf(0, 0, 1, 1)); uint(3, 1); packed(4, listOf(command(1, 1), zigZag(200), zigZag(200))) }
            string(3, "name")
            string(3, "area_km2")
            message(4) { string(1, "HAMILTON") }
            message(4) { fixed64(3, 4.25.toBits()) }
        }.toBytes()
        return polygons + labels
    }

    private class Recorder(private val result: BytesResult) : BytesGet {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun get(url: String, headers: Map<String, String>): BytesResult {
            calls += url to headers
            return result
        }
    }

    private fun load(result: BytesResult): Pair<PreparedSuburbTile?, Recorder> {
        val recorder = Recorder(result)
        return runBlocking { SuburbTileLoader(config, recorder).load(tile) } to recorder
    }

    @Test
    fun `a tile is requested with the key as a header and not in the url`() {
        val (_, recorder) = load(BytesResult.Response(204, ByteArray(0)))
        val (url, headers) = recorder.calls.single()
        assertEquals("http://host:8080/tiles/suburbs/10/943/611.pbf", url)
        assertEquals(mapOf("X-API-Key" to "secret-key-123456"), headers)
    }

    @Test
    fun `a 200 tile is decoded into outlines and named labels`() {
        val prepared = assertNotNull(load(BytesResult.Response(200, suburbTile())).first)
        assertNotNull(prepared.outlines)
        val label = prepared.labels.single()
        assertEquals("HAMILTON", label.name)
        assertEquals(200f, label.x)
        assertEquals(200f, label.y)
        assertEquals(4.25, label.areaKm2)
        assertEquals(4096, prepared.extent)
    }

    @Test
    fun `a 204 tile is empty but cacheable`() {
        val prepared = assertNotNull(load(BytesResult.Response(204, ByteArray(0))).first)
        assertSame(PreparedSuburbTile.EMPTY, prepared)
        assertTrue(prepared.isEmpty)
    }

    @Test
    fun `failures are null so the tile can be retried`() {
        assertNull(load(BytesResult.Response(401, ByteArray(0))).first)
        assertNull(load(BytesResult.Response(503, ByteArray(0))).first)
        assertNull(load(BytesResult.Failure("connection refused")).first)
    }

    @Test
    fun `a corrupt 200 body is null, not a crash`() {
        assertNull(load(BytesResult.Response(200, byteArrayOf(0x1A, 0x7F, 0x01))).first)
    }

    @Test
    fun `a tile without suburb layers prepares as empty`() {
        val other = ProtoWriter().message(3) { string(1, "something_else") }.toBytes()
        assertTrue(PreparedSuburbTile.from(VectorTileDecoder.decode(other)).isEmpty)
    }

    @Test
    fun `labels without a name or without a point are skipped`() {
        val point = listOf(command(1, 1), zigZag(10), zigZag(20))
        val bytes = ProtoWriter().message(3) {
            string(1, "suburb_label")
            message(2) { packed(2, emptyList()); uint(3, 1); packed(4, point) }               // no name tags
            message(2) { packed(2, listOf(0, 0, 1, 1)); uint(3, 1); packed(4, emptyList()) }  // no geometry
            message(2) { packed(2, listOf(0, 2, 1, 3)); uint(3, 1); packed(4, point) }        // valid
            string(3, "name")
            string(3, "area_km2")
            message(4) { string(1, "Nameless-but-pointless") }
            message(4) { fixed64(3, 1.0.toBits()) }
            message(4) { string(1, "Valid") }
            message(4) { fixed64(3, 2.0.toBits()) }
        }.toBytes()
        val labels = PreparedSuburbTile.from(VectorTileDecoder.decode(bytes)).labels
        assertEquals(listOf("Valid"), labels.map { it.name })
        assertEquals(10f, labels.single().x)
        assertEquals(2.0, labels.single().areaKm2)
    }
}

class PreparedSuburbShapesTest {
    private val square = listOf(
        au.homefinder.app.map.vector.ProtoWriter.command(1, 1), au.homefinder.app.map.vector.ProtoWriter.zigZag(100), au.homefinder.app.map.vector.ProtoWriter.zigZag(100),
        au.homefinder.app.map.vector.ProtoWriter.command(2, 3), au.homefinder.app.map.vector.ProtoWriter.zigZag(200), au.homefinder.app.map.vector.ProtoWriter.zigZag(0),
        au.homefinder.app.map.vector.ProtoWriter.zigZag(0), au.homefinder.app.map.vector.ProtoWriter.zigZag(200), au.homefinder.app.map.vector.ProtoWriter.zigZag(-200), au.homefinder.app.map.vector.ProtoWriter.zigZag(0),
        au.homefinder.app.map.vector.ProtoWriter.command(7, 1),
    )

    private fun tile(vararg ids: String?): au.homefinder.app.map.vector.VectorTile {
        val bytes = au.homefinder.app.map.vector.ProtoWriter().message(3) {
            string(1, "suburb")
            ids.forEachIndexed { i, id ->
                message(2) { if (id != null) packed(2, listOf(0, i.toLong())); uint(3, 3); packed(4, square) }
            }
            string(3, "suburb_id")
            ids.filterNotNull().forEach { id -> message(4) { string(1, id) } }
        }.toBytes()
        return au.homefinder.app.map.vector.VectorTileDecoder.decode(bytes)
    }

    @Test
    fun `each suburb keeps its own shape under its id and all are in the combined outline`() {
        val prepared = PreparedSuburbTile.from(tile("9001", "9002"))
        assertEquals(setOf("9001", "9002"), prepared.bySuburb.keys)
        assertNotNull(prepared.outlines)
    }

    @Test
    fun `a polygon without an id is outlined but cannot be highlighted`() {
        val prepared = PreparedSuburbTile.from(tile(null))
        assertNotNull(prepared.outlines)
        assertTrue(prepared.bySuburb.isEmpty())
    }

    @Test
    fun `the same suburb split over features shares one shape`() {
        val prepared = PreparedSuburbTile.from(tile("9001", "9001"))
        assertEquals(setOf("9001"), prepared.bySuburb.keys)
    }
}
