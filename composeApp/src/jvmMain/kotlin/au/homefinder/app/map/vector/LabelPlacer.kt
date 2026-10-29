package au.homefinder.app.map.vector

/** A label wanting a place on screen. Lower [priority] values are placed first and win collisions. */
class LabelCandidate<T>(val item: T, val centreX: Float, val centreY: Float, val width: Float, val height: Float, val priority: Int)

class PlacedLabel<T>(val item: T, val left: Float, val top: Float)

/** Greedy label placement: highest priority first, dropping labels that overlap one already placed or leave the view. */
object LabelPlacer {
    fun <T> place(
        candidates: List<LabelCandidate<T>>,
        viewWidth: Float,
        viewHeight: Float,
        padding: Float,
    ): List<PlacedLabel<T>> {
        val placed = ArrayList<PlacedLabel<T>>()
        val boxes = ArrayList<FloatArray>() // left, top, right, bottom, padded
        for (candidate in candidates.sortedBy { it.priority }) {
            val left = candidate.centreX - candidate.width / 2
            val top = candidate.centreY - candidate.height / 2
            val right = left + candidate.width
            val bottom = top + candidate.height
            val onScreen = right > 0 && bottom > 0 && left < viewWidth && top < viewHeight
            val clear = boxes.none { it[0] < right + padding && it[2] > left - padding && it[1] < bottom + padding && it[3] > top - padding }
            if (onScreen && clear) {
                placed.add(PlacedLabel(candidate.item, left, top))
                boxes.add(floatArrayOf(left, top, right, bottom))
            }
        }
        return placed
    }
}
