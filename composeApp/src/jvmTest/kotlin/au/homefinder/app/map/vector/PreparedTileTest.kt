package au.homefinder.app.map.vector

import au.homefinder.app.map.vector.ProtoWriter.Companion.command
import au.homefinder.app.map.vector.ProtoWriter.Companion.zigZag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreparedTileTest {
    private val moveTo = 1L
    private val lineTo = 2L
    private val closePath = 7L

    private fun feature(type: Int, cls: String?, geometry: List<Long>): ProtoWriter.() -> Unit = {
        if (cls != null) packed(2, listOf(0, 0))
        uint(3, type.toLong())
        packed(4, geometry)
    }

    private val square = listOf(
        command(moveTo.toInt(), 1), zigZag(0), zigZag(0),
        command(lineTo.toInt(), 2), zigZag(10), zigZag(0), zigZag(0), zigZag(10),
        command(closePath.toInt(), 1),
    )
    private val line = listOf(command(moveTo.toInt(), 1), zigZag(0), zigZag(0), command(lineTo.toInt(), 1), zigZag(50), zigZag(50))
    private val point = listOf(command(moveTo.toInt(), 1), zigZag(100), zigZag(200))

    private fun layer(name: String, cls: String?, type: Int, geometry: List<Long>, extra: ProtoWriter.() -> Unit = {}): ByteArray =
        ProtoWriter().message(3) {
            string(1, name)
            message(2, feature(type, cls, geometry))
            string(3, "class")
            message(4) { string(1, cls ?: "") }
            extra()
        }.toBytes()

    @Test
    fun `polygons and lines are grouped by layer and class`() {
        val roads = ProtoWriter().message(3) {
            string(1, "transportation")
            message(2) { packed(2, listOf(0, 0)); uint(3, 2); packed(4, line) }
            message(2) { packed(2, listOf(0, 1)); uint(3, 2); packed(4, line) }
            string(3, "class")
            message(4) { string(1, "primary") }
            message(4) { string(1, "minor") }
        }.toBytes()
        val prepared = PreparedTile.from(VectorTileDecoder.decode(layer("water", "ocean", 3, square) + roads))
        assertNotNull(prepared.paths["water"]?.get("ocean"))
        assertEquals(setOf<String?>("primary", "minor"), prepared.paths["transportation"]?.keys)
    }

    @Test
    fun `features with the wrong geometry type for their layer are ignored`() {
        val prepared = PreparedTile.from(VectorTileDecoder.decode(layer("water", "ocean", 2, line)))
        assertNull(prepared.paths["water"])
    }

    @Test
    fun `layers that are not drawn are skipped`() {
        val prepared = PreparedTile.from(VectorTileDecoder.decode(layer("housenumber", null, 1, point)))
        assertTrue(prepared.paths.isEmpty() && prepared.labels.isEmpty())
    }

    @Test
    fun `place features become labels at their tile position`() {
        val bytes = ProtoWriter().message(3) {
            string(1, "place")
            message(2) { packed(2, listOf(0, 0, 1, 1)); uint(3, 1); packed(4, point) }
            string(3, "class")
            string(3, "name")
            message(4) { string(1, "suburb") }
            message(4) { string(1, "Hamilton") }
        }.toBytes()
        val label = PreparedTile.from(VectorTileDecoder.decode(bytes)).labels.single()
        assertEquals("Hamilton", label.text)
        assertEquals(100f, label.x)
        assertEquals(200f, label.y)
    }

    @Test
    fun `places of an unknown class and unnamed places are not labelled`() {
        val bytes = ProtoWriter().message(3) {
            string(1, "place")
            message(2) { packed(2, listOf(0, 0, 1, 1)); uint(3, 1); packed(4, point) }
            message(2) { packed(2, listOf(0, 0)); uint(3, 1); packed(4, point) }
            string(3, "class")
            string(3, "name")
            message(4) { string(1, "continent") }
            message(4) { string(1, "Somewhere") }
        }.toBytes()
        assertTrue(PreparedTile.from(VectorTileDecoder.decode(bytes)).labels.isEmpty())
    }

    @Test
    fun `extent comes from the tile`() {
        val bytes = layer("water", "ocean", 3, square) { uint(5, 8192) }
        assertEquals(8192, PreparedTile.from(VectorTileDecoder.decode(bytes)).extent)
    }
}
