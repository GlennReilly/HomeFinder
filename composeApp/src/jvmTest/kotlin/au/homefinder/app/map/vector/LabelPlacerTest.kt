package au.homefinder.app.map.vector

import kotlin.test.Test
import kotlin.test.assertEquals

class LabelPlacerTest {
    private fun label(name: String, x: Float, y: Float, priority: Int) = LabelCandidate(name, x, y, 40f, 12f, priority)

    private fun placed(vararg labels: LabelCandidate<String>) =
        LabelPlacer.place(labels.toList(), 400f, 300f, padding = 2f).map { it.item }

    @Test
    fun `the higher priority label wins a collision`() {
        assertEquals(listOf("city"), placed(label("suburb", 100f, 100f, 5), label("city", 110f, 102f, 1)))
    }

    @Test
    fun `labels that do not overlap are all kept`() {
        assertEquals(setOf("a", "b"), placed(label("a", 50f, 50f, 1), label("b", 200f, 50f, 2)).toSet())
    }

    @Test
    fun `padding keeps near-touching labels apart`() {
        // 40 px wide labels centred 41 px apart leave a 1 px gap, less than the 2 px padding.
        assertEquals(listOf("a"), placed(label("a", 100f, 100f, 1), label("b", 141f, 100f, 2)))
    }

    @Test
    fun `labels fully off screen are dropped`() {
        assertEquals(emptyList(), placed(label("far", 900f, 100f, 1), label("above", 100f, -50f, 1)))
    }

    @Test
    fun `labels partly on screen are kept`() {
        assertEquals(listOf("edge"), placed(label("edge", 5f, 100f, 1)))
    }

    @Test
    fun `labels with the same key stay apart by the minimum distance`() {
        val near = LabelCandidate("a", 100f, 100f, 20f, 8f, 1, key = "Main St", minSameKeyDistance = 200f)
        val alsoNear = LabelCandidate("b", 250f, 100f, 20f, 8f, 2, key = "Main St", minSameKeyDistance = 200f)
        val far = LabelCandidate("c", 350f, 100f, 20f, 8f, 3, key = "Main St", minSameKeyDistance = 200f)
        val other = LabelCandidate("d", 250f, 200f, 20f, 8f, 4, key = "High St", minSameKeyDistance = 200f)
        val result = LabelPlacer.place(listOf(near, alsoNear, far, other), 400f, 300f, 2f).map { it.item }
        assertEquals(listOf("a", "c", "d"), result)
    }
}
