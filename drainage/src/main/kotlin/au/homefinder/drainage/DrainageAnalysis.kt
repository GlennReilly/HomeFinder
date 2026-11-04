package au.homefinder.drainage

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Per-pixel results of [DrainageAnalysis.analyse], laid out like the grid they came from. NaN means unknown: the pixel has no data,
 * or its water leaves the grid without meeting a stream.
 */
class DrainageResult(
    val columns: Int,
    val rows: Int,
    /** Height of the pixel's ground above the first stream pixel its water reaches, in metres (never negative). */
    val heightAboveDrainageM: FloatArray,
    /** How much ground drains through that stream pixel, in hectares: the size of the stream the pixel drains to. */
    val streamCatchmentHa: FloatArray,
)

/**
 * Height above nearest drainage (ADR 0040). Pits are filled by a priority flood so every pixel has a path off the grid; each pixel
 * drains to the neighbour its flood reached it from; a stream is any pixel that drains at least [streamCatchmentHa] hectares; and a
 * pixel's result is its ground height above the stream pixel its path first meets. Heights are held in whole millimetres so the
 * ordering is exact and repeatable.
 */
object DrainageAnalysis {
    /** A stream starts where this much ground drains through a pixel (tested against the council's flood corridor in ELERMORE VALE). */
    const val DEFAULT_STREAM_CATCHMENT_HA: Double = 5.0

    /** Heights outside this range are fill values, not ground (the same bounds as the DEM load, config/dem.env). */
    const val MIN_VALID_HEIGHT_M: Float = -50f
    const val MAX_VALID_HEIGHT_M: Float = 1_500f

    private const val SQUARE_METRES_PER_HECTARE = 10_000.0
    private const val MILLIMETRES_PER_METRE = 1_000f
    private const val NO_DATA_MM = Int.MIN_VALUE / 2
    private const val NO_STREAM_MM = Int.MIN_VALUE
    private const val KEY_OFFSET = 1L shl 30
    private const val KEY_SHIFT = 32
    private const val INDEX_MASK = 0xFFFF_FFFFL
    private const val NO_PARENT = -1

    fun analyse(grid: ElevationGrid, streamCatchmentHa: Double = DEFAULT_STREAM_CATCHMENT_HA): DrainageResult {
        require(streamCatchmentHa > 0.0) { "The stream size must be positive" }
        val columns = grid.columns
        val rows = grid.rows
        val count = grid.cellCount
        val ground = IntArray(count) { toMillimetres(grid.heightsM[it]) }
        val filled = ground.copyOf()

        val parent = IntArray(count) { NO_PARENT }
        val order = floodOrder(columns, rows, ground, filled, parent)

        val drained = IntArray(count) { if (ground[it] == NO_DATA_MM) 0 else 1 }
        for (position in order.indices.reversed()) {
            val cell = order[position]
            val downstream = parent[cell]
            if (downstream != NO_PARENT) drained[downstream] += drained[cell]
        }

        val streamCells = max(1, ceil(streamCatchmentHa * SQUARE_METRES_PER_HECTARE / grid.cellAreaM2).toInt())
        val streamHeight = IntArray(count) { NO_STREAM_MM }
        val streamDrained = IntArray(count)
        for (cell in order) {
            if (ground[cell] == NO_DATA_MM) continue
            if (drained[cell] >= streamCells) {
                streamHeight[cell] = filled[cell]
                streamDrained[cell] = drained[cell]
            } else if (parent[cell] != NO_PARENT) {
                streamHeight[cell] = streamHeight[parent[cell]]
                streamDrained[cell] = streamDrained[parent[cell]]
            }
        }

        val hectaresPerCell = (grid.cellAreaM2 / SQUARE_METRES_PER_HECTARE).toFloat()
        val above = FloatArray(count) { cell ->
            if (ground[cell] == NO_DATA_MM || streamHeight[cell] == NO_STREAM_MM) Float.NaN
            else max(0, ground[cell] - streamHeight[cell]) / MILLIMETRES_PER_METRE
        }
        val catchment = FloatArray(count) { cell -> if (above[cell].isNaN()) Float.NaN else streamDrained[cell] * hectaresPerCell }
        return DrainageResult(columns, rows, above, catchment)
    }

    private fun toMillimetres(heightM: Float): Int =
        if (heightM.isNaN() || heightM < MIN_VALID_HEIGHT_M || heightM > MAX_VALID_HEIGHT_M) NO_DATA_MM
        else (heightM * MILLIMETRES_PER_METRE).roundToInt()

    /**
     * Floods inwards from the grid's edge and from every no-data pixel (both are outlets), always taking the lowest pixel first. Each
     * pixel's [filled] height is raised to its flood's level where it sat in a pit, and its [parent] is the pixel that flooded it.
     * Returns the pixels in the order they were flooded, so a parent always comes before its children.
     */
    private fun floodOrder(columns: Int, rows: Int, ground: IntArray, filled: IntArray, parent: IntArray): IntArray {
        val count = columns * rows
        val queued = BooleanArray(count)
        val heap = LongHeap(count)
        for (cell in 0 until count) {
            val row = cell / columns
            val column = cell % columns
            if (row == 0 || column == 0 || row == rows - 1 || column == columns - 1 || ground[cell] == NO_DATA_MM) {
                queued[cell] = true
                heap.push(key(filled[cell], cell))
            }
        }
        val order = IntArray(count)
        var flooded = 0
        while (!heap.isEmpty()) {
            val cell = (heap.pop() and INDEX_MASK).toInt()
            order[flooded++] = cell
            val level = filled[cell]
            val row = cell / columns
            val column = cell % columns
            for (dRow in -1..1) {
                val nextRow = row + dRow
                if (nextRow < 0 || nextRow >= rows) continue
                for (dColumn in -1..1) {
                    val nextColumn = column + dColumn
                    if ((dRow == 0 && dColumn == 0) || nextColumn < 0 || nextColumn >= columns) continue
                    val next = nextRow * columns + nextColumn
                    if (queued[next]) continue
                    queued[next] = true
                    parent[next] = cell
                    if (filled[next] < level) filled[next] = level
                    heap.push(key(filled[next], next))
                }
            }
        }
        return order
    }

    private fun key(heightMm: Int, cell: Int): Long = ((heightMm + KEY_OFFSET) shl KEY_SHIFT) or cell.toLong()

    /** A binary min-heap of longs with room for a known number of entries (no boxing, so millions of pixels stay quick). */
    private class LongHeap(capacity: Int) {
        private val items = LongArray(capacity)
        private var size = 0

        fun isEmpty(): Boolean = size == 0

        fun push(value: Long) {
            var child = size++
            while (child > 0) {
                val up = (child - 1) ushr 1
                if (items[up] <= value) break
                items[child] = items[up]
                child = up
            }
            items[child] = value
        }

        fun pop(): Long {
            val top = items[0]
            val last = items[--size]
            var hole = 0
            while (true) {
                var child = 2 * hole + 1
                if (child >= size) break
                if (child + 1 < size && items[child + 1] < items[child]) child++
                if (items[child] >= last) break
                items[hole] = items[child]
                hole = child
            }
            items[hole] = last
            return top
        }
    }
}
