package au.homefinder.app.map.vector

import kotlin.math.hypot

/**
 * A label wanting a place on screen. Lower [priority] values are placed first and win collisions. Labels sharing a
 * [key] (the same street or park name) are kept at least [minSameKeyDistance] pixels apart so a name is not repeated
 * on every tile or segment.
 */
class LabelCandidate<T>(
    val item: T,
    val centreX: Float,
    val centreY: Float,
    val width: Float,
    val height: Float,
    val priority: Int,
    val key: String? = null,
    val minSameKeyDistance: Float = 0f,
)

class PlacedLabel<T>(val item: T, val left: Float, val top: Float)

/** Greedy label placement: highest priority first, dropping labels that overlap one already placed or leave the view. */
object LabelPlacer {
    private class Taken(val left: Float, val top: Float, val right: Float, val bottom: Float, val key: String?, val x: Float, val y: Float)

    fun <T> place(
        candidates: List<LabelCandidate<T>>,
        viewWidth: Float,
        viewHeight: Float,
        padding: Float,
    ): List<PlacedLabel<T>> {
        val placed = ArrayList<PlacedLabel<T>>()
        val taken = ArrayList<Taken>()
        for (candidate in candidates.sortedBy { it.priority }) {
            val left = candidate.centreX - candidate.width / 2
            val top = candidate.centreY - candidate.height / 2
            val right = left + candidate.width
            val bottom = top + candidate.height
            val onScreen = right > 0 && bottom > 0 && left < viewWidth && top < viewHeight
            if (!onScreen) continue
            val overlaps = taken.any { it.left < right + padding && it.right > left - padding && it.top < bottom + padding && it.bottom > top - padding }
            val repeated = candidate.key != null && taken.any {
                it.key == candidate.key && hypot(it.x - candidate.centreX, it.y - candidate.centreY) < candidate.minSameKeyDistance
            }
            if (!overlaps && !repeated) {
                placed.add(PlacedLabel(candidate.item, left, top))
                taken.add(Taken(left, top, right, bottom, candidate.key, candidate.centreX, candidate.centreY))
            }
        }
        return placed
    }
}
