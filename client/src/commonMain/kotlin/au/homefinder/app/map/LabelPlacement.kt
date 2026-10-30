package au.homefinder.app.map

/** A point in longitude and latitude (GDA2020 degrees). */
data class LonLat(val longitude: Double, val latitude: Double)

/** A box in degrees. [intersect] is null when the boxes do not overlap. */
data class Box(val west: Double, val south: Double, val east: Double, val north: Double) {
    fun contains(point: LonLat): Boolean = point.longitude in west..east && point.latitude in south..north

    fun intersect(other: Box): Box? {
        val box = Box(maxOf(west, other.west), maxOf(south, other.south), minOf(east, other.east), minOf(north, other.north))
        return if (box.west <= box.east && box.south <= box.north) box else null
    }

    val center: LonLat get() = LonLat((west + east) / 2, (south + north) / 2)
}

/** One piece of a suburb's outline as the map holds it: the outer ring first, then any holes, each a closed list of points. */
class OutlinePiece(val rings: List<List<LonLat>>) {
    /** Even-odd rule over every ring, so a point inside a hole is outside the piece. */
    fun contains(point: LonLat): Boolean {
        var inside = false
        for (ring in rings) {
            var previous = ring.lastOrNull() ?: continue
            for (current in ring) {
                val crosses = (current.latitude > point.latitude) != (previous.latitude > point.latitude) &&
                    point.longitude < (previous.longitude - current.longitude) * (point.latitude - current.latitude) /
                    (previous.latitude - current.latitude) + current.longitude
                if (crosses) inside = !inside
                previous = current
            }
        }
        return inside
    }

    val vertices: Sequence<LonLat> get() = rings.asSequence().flatten()
}

/**
 * Chooses where to write a suburb's name and score so that exactly one label is on screen and it lies inside the suburb
 * (ADR 0028). The suburb's usual label point is used whenever it is in view; once you have zoomed in past it, the label
 * moves to the point of the visible part of the suburb nearest the middle of the view.
 */
object LabelPlacement {
    /** Candidate points per side tried inside the visible part of a suburb. */
    const val GRID_STEPS: Int = 12

    fun place(pieces: List<OutlinePiece>, view: Box, preferred: LonLat?): LonLat? {
        if (preferred != null && view.contains(preferred)) return preferred
        val vertices = pieces.asSequence().flatMap { it.vertices }.toList()
        if (vertices.isEmpty()) return null
        val extent = Box(
            vertices.minOf { it.longitude }, vertices.minOf { it.latitude },
            vertices.maxOf { it.longitude }, vertices.maxOf { it.latitude },
        )
        val visible = extent.intersect(view) ?: return null
        val middle = visible.center
        val candidates = buildList {
            for (i in 0..GRID_STEPS) for (j in 0..GRID_STEPS) {
                add(LonLat(visible.west + (visible.east - visible.west) * i / GRID_STEPS, visible.south + (visible.north - visible.south) * j / GRID_STEPS))
            }
        }
        val inside = candidates.filter { candidate -> pieces.any { it.contains(candidate) } }
        if (inside.isNotEmpty()) return inside.minBy { squaredDistance(it, middle) }
        // A thin sliver of the suburb: fall back to an outline vertex that is in view.
        return vertices.filter { view.contains(it) }.minByOrNull { squaredDistance(it, middle) }
    }

    private fun squaredDistance(a: LonLat, b: LonLat): Double {
        val dx = a.longitude - b.longitude
        val dy = a.latitude - b.latitude
        return dx * dx + dy * dy
    }
}
