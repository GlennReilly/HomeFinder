package au.homefinder.census

import java.nio.file.Files
import java.nio.file.Path

/** Which kind of school a catchment file is for: the primary file and the secondary file of the Department's download (ADR 0056). */
enum class CatchmentLevel(val fileName: String) {
    PRIMARY("catchments_primary"),
    HIGH("catchments_secondary"),
}

/** One current catchment (intake area) of a school, ready to load: the shape as WKT in GDA94 (EPSG:4283), as published. */
data class Catchment(
    val level: CatchmentLevel,
    val schoolCode: String,
    val catchType: String,
    val name: String,
    val addedOn: String,
    /** The year groups the zone serves, for example "K-6" or "7-12". */
    val years: String,
    val wkt: String,
)

sealed interface CatchmentsResult {
    data class Read(val catchments: List<Catchment>, val enrolmentYear: Int?, val skipped: Int) : CatchmentsResult
    data class Failed(val reason: String) : CatchmentsResult
}

/**
 * Reads the current primary and secondary catchments from the unpacked Department of Education download (ADR 0056); the file of future
 * catchments is left alone. A record with no shape or no numeric school code is skipped (and counted).
 */
object Catchments {
    private const val INFO_FILE = "catchment_sf_info.json"
    private val ENROLMENT_YEAR = Regex(""""current_enrolment_year"\s*:\s*(\d{4})""")
    private val YEAR_COLUMNS = listOf("KINDERGART") + (1..12).map { "YEAR$it" }
    private const val YES = "Y"

    fun read(directory: Path): CatchmentsResult {
        val catchments = ArrayList<Catchment>()
        var skipped = 0
        CatchmentLevel.entries.forEach { level ->
            val shp = directory.resolve(level.fileName + ".shp")
            val dbf = directory.resolve(level.fileName + ".dbf")
            if (!Files.isRegularFile(shp) || !Files.isRegularFile(dbf)) return CatchmentsResult.Failed("No ${level.fileName}.shp and .dbf under $directory; run download-catchments.sh")
            val result = Shapefile.read(shp, dbf) { record ->
                val code = record.attributes["USE_ID"].orEmpty()
                val wkt = record.shape?.toWkt()
                if (wkt == null || wkt == PolygonShape.EMPTY_WKT || code.isEmpty() || !code.all(Char::isDigit)) {
                    skipped++
                } else {
                    catchments += Catchment(
                        level, code, record.attributes["CATCH_TYPE"].orEmpty(), record.attributes["USE_DESC"].orEmpty(),
                        record.attributes["ADD_DATE"].orEmpty(), years(record.attributes), wkt,
                    )
                }
            }
            if (result is ShapefileResult.Failed) return CatchmentsResult.Failed(result.reason)
        }
        if (catchments.isEmpty()) return CatchmentsResult.Failed("$directory holds no catchments")
        return CatchmentsResult.Read(catchments, enrolmentYear(directory.resolve(INFO_FILE)), skipped)
    }

    private fun enrolmentYear(info: Path): Int? =
        if (Files.isRegularFile(info)) ENROLMENT_YEAR.find(Files.readString(info))?.groupValues?.get(1)?.toInt() else null

    /** "K-6" from the Y/N flags of kindergarten and years 1 to 12: the first and last year served, or "K, 3" and so on when they are not one run. */
    internal fun years(attributes: Map<String, String>): String {
        val served = YEAR_COLUMNS.indices.filter { attributes[YEAR_COLUMNS[it]] == YES }
        if (served.isEmpty()) return ""
        fun label(index: Int) = if (index == 0) "K" else index.toString()
        val contiguous = served.last() - served.first() + 1 == served.size
        return when {
            served.size == 1 -> label(served.first())
            contiguous -> label(served.first()) + "-" + label(served.last())
            else -> served.joinToString(", ") { label(it) }
        }
    }
}

/** The tool's output: one row per catchment, read by `pipeline/sql/catchments.sql`. */
object CatchmentsCsv {
    const val HEADER: String = "level,school_code,catch_type,name,added_on,years,enrolment_year,wkt"

    fun format(result: CatchmentsResult.Read): List<String> = listOf(HEADER) + result.catchments.map {
        listOf(
            it.level.name, it.schoolCode, quoted(it.catchType), quoted(it.name), it.addedOn, quoted(it.years), result.enrolmentYear?.toString().orEmpty(), quoted(it.wkt),
        ).joinToString(",")
    }

    private fun quoted(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
