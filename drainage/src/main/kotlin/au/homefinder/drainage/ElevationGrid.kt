package au.homefinder.drainage

import kotlin.math.cos
import kotlin.math.floor

/** Metres on the ground per degree of latitude (mean Earth radius); a degree of longitude is this times the cosine of the latitude. */
const val METRES_PER_DEGREE: Double = 111_195.0

/**
 * A rectangle of bare-earth heights in metres on a regular grid of degrees (the DEM's own grid, GDA94). Row 0 is the northern edge and
 * column 0 the western edge. A height of NaN means no data.
 */
class ElevationGrid(
    val columns: Int,
    val rows: Int,
    val westDeg: Double,
    val northDeg: Double,
    val pixelDegX: Double,
    val pixelDegY: Double,
    val heightsM: FloatArray,
) {
    init {
        require(columns > 0 && rows > 0) { "A grid needs at least one column and one row" }
        require(pixelDegX > 0.0 && pixelDegY > 0.0) { "Pixel sizes are positive degrees" }
        require(heightsM.size == columns * rows) { "Expected ${columns * rows} heights but got ${heightsM.size}" }
    }

    val cellCount: Int get() = columns * rows

    private val centreLatDeg: Double get() = northDeg - rows * pixelDegY / 2.0

    /** The ground area of one pixel in square metres, at the grid's middle latitude. */
    val cellAreaM2: Double
        get() = pixelDegY * METRES_PER_DEGREE * pixelDegX * METRES_PER_DEGREE * cos(Math.toRadians(centreLatDeg))

    /** The index (row * columns + column) of the pixel holding the point, or null when the point is outside the grid. */
    fun indexAt(longitude: Double, latitude: Double): Int? {
        val column = floor((longitude - westDeg) / pixelDegX).toInt()
        val row = floor((northDeg - latitude) / pixelDegY).toInt()
        return if (column in 0 until columns && row in 0 until rows) row * columns + column else null
    }
}
