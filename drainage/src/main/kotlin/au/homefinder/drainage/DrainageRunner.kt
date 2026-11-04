package au.homefinder.drainage

import kotlin.math.floor

/**
 * Works through the lots in blocks of ground so no window is bigger than a few million pixels: each block's lots are placed on a window
 * of the DEM widened by a margin, so streams just outside the block still collect their catchments (ADR 0040).
 */
object DrainageRunner {
    /** Lots are grouped into blocks this many degrees square (about 7 km). */
    const val BLOCK_DEG: Double = 0.08

    /** Each block's window reaches this far beyond the lots in it (about 1.9 km), so catchments are not cut off at the block's edge. */
    const val MARGIN_DEG: Double = 0.02

    fun run(
        tiles: DemTiles,
        points: List<LotPoint>,
        streamCatchmentHa: Double = DrainageAnalysis.DEFAULT_STREAM_CATCHMENT_HA,
        log: (String) -> Unit = {},
    ): List<LotDrainage> {
        val blocks = points.groupBy { blockOf(it) }.toSortedMap(compareBy({ it.first }, { it.second }))
        val results = ArrayList<LotDrainage>(points.size)
        val warned = HashSet<java.nio.file.Path>()
        for ((number, entry) in blocks.entries.withIndex()) {
            val lots = entry.value
            val window = tiles.window(
                lots.minOf { it.longitude } - MARGIN_DEG, lots.minOf { it.latitude } - MARGIN_DEG,
                lots.maxOf { it.longitude } + MARGIN_DEG, lots.maxOf { it.latitude } + MARGIN_DEG,
            )
            // The same empty or cut-short tile can reach many blocks; it is reported once.
            window.unreadable.filter { warned.add(it) }
                .forEach { log("Warning: ${it.fileName} is empty or cut short; its pixels count as no data") }
            val grid = window.grid
            if (grid == null) {
                log("Block ${number + 1} of ${blocks.size}: no DEM covers its ${lots.size} lots")
                continue
            }
            val analysis = DrainageAnalysis.analyse(grid, streamCatchmentHa)
            var placed = 0
            for (lot in lots) {
                val cell = grid.indexAt(lot.longitude, lot.latitude) ?: continue
                val height = analysis.heightAboveDrainageM[cell]
                if (height.isNaN()) continue
                results += LotDrainage(lot.lotId, height.toDouble(), analysis.streamCatchmentHa[cell].toDouble())
                placed++
            }
            log("Block ${number + 1} of ${blocks.size}: ${grid.columns} x ${grid.rows} pixels, $placed of ${lots.size} lots placed")
        }
        return results.sortedBy { it.lotId }
    }

    private fun blockOf(point: LotPoint): Pair<Int, Int> =
        floor(point.longitude / BLOCK_DEG).toInt() to floor(point.latitude / BLOCK_DEG).toInt()
}
