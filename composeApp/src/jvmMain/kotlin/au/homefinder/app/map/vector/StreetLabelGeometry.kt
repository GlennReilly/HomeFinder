package au.homefinder.app.map.vector

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Where to draw text along a line: the centre and the rotation in degrees, never upside down. */
class LabelAnchor(val x: Float, val y: Float, val angleDegrees: Float)

object StreetLabelGeometry {
    private const val HALF_TURN_DEGREES = 180f
    private const val QUARTER_TURN_DEGREES = 90f
    private const val FIT_MARGIN = 1.1f

    /**
     * Centres text of [textWidth] on the middle of a polyline of x,y pairs. Returns null if the line is too short or
     * bends more than [maxDeviation] pixels away from the straight chord under the text, since text is drawn straight.
     */
    fun anchorAlongLine(points: FloatArray, textWidth: Float, maxDeviation: Float): LabelAnchor? {
        val count = points.size / 2
        if (count < 2) return null
        val cumulative = FloatArray(count)
        for (i in 1 until count) {
            cumulative[i] = cumulative[i - 1] + hypot(points[i * 2] - points[(i - 1) * 2], points[i * 2 + 1] - points[(i - 1) * 2 + 1])
        }
        val total = cumulative[count - 1]
        if (total < textWidth * FIT_MARGIN) return null
        val start = total / 2 - textWidth / 2
        val end = total / 2 + textWidth / 2
        val (startX, startY) = pointAt(points, cumulative, start)
        val (endX, endY) = pointAt(points, cumulative, end)
        val chord = hypot(endX - startX, endY - startY)
        if (chord <= 0f) return null
        for (i in 0 until count) {
            if (cumulative[i] <= start || cumulative[i] >= end) continue
            val distance = abs((endX - startX) * (startY - points[i * 2 + 1]) - (startX - points[i * 2]) * (endY - startY)) / chord
            if (distance > maxDeviation) return null
        }
        var angle = Math.toDegrees(atan2((endY - startY).toDouble(), (endX - startX).toDouble())).toFloat()
        if (angle > QUARTER_TURN_DEGREES) angle -= HALF_TURN_DEGREES
        if (angle <= -QUARTER_TURN_DEGREES) angle += HALF_TURN_DEGREES
        return LabelAnchor((startX + endX) / 2, (startY + endY) / 2, angle)
    }

    private fun pointAt(points: FloatArray, cumulative: FloatArray, distance: Float): Pair<Float, Float> {
        var i = 1
        while (i < cumulative.size - 1 && cumulative[i] < distance) i++
        val segment = cumulative[i] - cumulative[i - 1]
        val t = if (segment > 0f) (distance - cumulative[i - 1]) / segment else 0f
        return Pair(
            points[(i - 1) * 2] + (points[i * 2] - points[(i - 1) * 2]) * t,
            points[(i - 1) * 2 + 1] + (points[i * 2 + 1] - points[(i - 1) * 2 + 1]) * t,
        )
    }
}
