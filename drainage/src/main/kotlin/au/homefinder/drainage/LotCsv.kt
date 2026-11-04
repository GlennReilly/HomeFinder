package au.homefinder.drainage

/** A lot's representative point (inside the lot), as exported by pipeline/scripts/load-drainage.sh. */
data class LotPoint(val lotId: String, val longitude: Double, val latitude: Double)

/** One lot's result: its height above the stream its water reaches, and how big that stream's catchment is. */
data class LotDrainage(val lotId: String, val heightAboveDrainageM: Double, val streamCatchmentHa: Double)

/** What [LotCsv.parsePoints] made of the input. */
sealed interface PointsResult {
    data class Parsed(val points: List<LotPoint>) : PointsResult
    data class Malformed(val lineNumber: Int, val reason: String) : PointsResult
}

/** The two CSV files of the tool: `lot_id,longitude,latitude` in, `lot_id,height_above_drainage_m,stream_catchment_ha` out. */
object LotCsv {
    const val POINTS_HEADER: String = "lot_id,longitude,latitude"
    const val RESULT_HEADER: String = "lot_id,height_above_drainage_m,stream_catchment_ha"
    private const val POINT_FIELDS = 3
    private const val DECIMALS = 2
    private const val FIRST_DATA_LINE = 2

    fun parsePoints(lines: List<String>): PointsResult {
        if (lines.firstOrNull()?.trim() != POINTS_HEADER) return PointsResult.Malformed(1, "Expected the header $POINTS_HEADER")
        val points = ArrayList<LotPoint>(lines.size - 1)
        for ((index, raw) in lines.withIndex().drop(1)) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val fields = line.split(',')
            if (fields.size != POINT_FIELDS) return PointsResult.Malformed(index + 1, "Expected $POINT_FIELDS fields but found ${fields.size}")
            val longitude = fields[1].toDoubleOrNull()
            val latitude = fields[2].toDoubleOrNull()
            if (fields[0].isBlank() || longitude == null || latitude == null) return PointsResult.Malformed(index + 1, "Unreadable lot or coordinates: $line")
            points += LotPoint(fields[0], longitude, latitude)
        }
        return PointsResult.Parsed(points)
    }

    fun formatResults(results: List<LotDrainage>): List<String> =
        listOf(RESULT_HEADER) + results.map { "${it.lotId},${round(it.heightAboveDrainageM)},${round(it.streamCatchmentHa)}" }

    private fun round(value: Double): String = "%.${DECIMALS}f".format(java.util.Locale.ROOT, value)
}
