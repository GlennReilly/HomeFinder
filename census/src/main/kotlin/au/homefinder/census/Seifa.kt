package au.homefinder.census

import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.xml.stream.XMLStreamException

/**
 * One suburb's Index of Relative Socio-economic Advantage and Disadvantage (IRSAD) with its score, and the deciles of the disadvantage (IRSD), economic
 * resources (IER) and education and occupation (IEO) indexes, and its population, from SEIFA 2021 (ADR 0049, 0050).
 */
data class SeifaSuburb(
    val salCode: String,
    val name: String,
    val irsadScore: Double,
    val irsadDecile: Int,
    val population: Int,
    /** The deciles of the other three indexes; null where the ABS gives the suburb no score for that index. */
    val irsdDecile: Int? = null,
    val ierDecile: Int? = null,
    val ieoDecile: Int? = null,
)

/** What [Seifa.read] found: the suburbs with an IRSAD score, and how many rows had none (areas the ABS gives no index). */
sealed interface SeifaResult {
    data class Read(val suburbs: List<SeifaSuburb>, val withoutScore: Int) : SeifaResult
    data class Failed(val reason: String) : SeifaResult
}

/**
 * Reads the ABS file "Suburbs and Localities, Indexes, SEIFA 2021.xlsx". Table 1 (the summary) has one row per suburb: code, name, then a
 * score and a decile for each of four indexes (IRSD, IRSAD, IER, IEO) and the usual resident population in column K. The workbook is plain
 * zipped XML (suburbs outside NSW are left out), read the way [SalNames] reads the Census workbook. The sheet is found by its header row, not its position.
 */
object Seifa {
    private const val CODE_COLUMN = 0
    private const val NAME_COLUMN = 1
    private const val IRSAD_SCORE_COLUMN = 4
    private const val IRSAD_DECILE_COLUMN = 5
    private const val IRSD_DECILE_COLUMN = 3
    private const val IER_DECILE_COLUMN = 7
    private const val IEO_DECILE_COLUMN = 9
    private const val POPULATION_COLUMN = 10
    private const val SCORE_HEADER_COLUMN = 2
    private const val CODE_HEADER = "2021 Suburbs and Localities (SAL) Code"
    private const val POPULATION_HEADER = "Usual Resident Population"
    private const val SCORE_HEADER = "Score"
    private const val SHARED_STRINGS = "xl/sharedStrings.xml"
    private const val SHEET_PREFIX = "xl/worksheets/sheet"
    const val SAL_PREFIX: String = "SAL"

    /** The file covers Australia; the first digit of a suburb code is the state, and 1 is New South Wales (so "Hamilton (Vic.)" is left out). */
    private const val NSW_CODE_PREFIX = "1"
    const val MIN_DECILE: Int = 1
    const val MAX_DECILE: Int = 10

    fun read(workbook: Path): SeifaResult = try {
        ZipFile(workbook.toFile()).use { zip ->
            val sharedEntry = zip.getEntry(SHARED_STRINGS) ?: return SeifaResult.Failed("$workbook has no $SHARED_STRINGS; it is not an xlsx workbook")
            val strings = zip.getInputStream(sharedEntry).use { SalNames.sharedStrings(it) }
            val suburbs = ArrayList<SeifaSuburb>()
            var withoutScore = 0
            var found = false
            zip.entries().asSequence().filter { it.name.startsWith(SHEET_PREFIX) && it.name.endsWith(".xml") }.forEach { sheet ->
                var inSummary = false
                zip.getInputStream(sheet).use {
                    SalNames.rows(it, strings) { row ->
                        if (isSummaryHeader(row)) {
                            inSummary = true
                            found = true
                        } else if (inSummary) {
                            when (val parsed = parse(row)) {
                                is Row.Suburb -> suburbs += parsed.value
                                Row.NoScore -> withoutScore++
                                Row.Other -> Unit
                            }
                        }
                    }
                }
            }
            when {
                !found -> SeifaResult.Failed("$workbook has no summary table (a header row with \"$POPULATION_HEADER\" in column K); is it the SAL SEIFA 2021 file?")
                suburbs.isEmpty() -> SeifaResult.Failed("$workbook's summary table has no suburb with an IRSAD score")
                else -> SeifaResult.Read(suburbs, withoutScore)
            }
        }
    } catch (failure: IOException) {
        SeifaResult.Failed("Could not read $workbook: ${failure.message}")
    } catch (failure: XMLStreamException) {
        SeifaResult.Failed("Could not parse $workbook: ${failure.message}")
    }

    /** Table 1's header: "...Code" in A, "Score" in C (the later tables have the population there) and the population heading in K. */
    internal fun isSummaryHeader(row: Map<Int, String>): Boolean =
        row[CODE_COLUMN]?.trim() == CODE_HEADER && row[SCORE_HEADER_COLUMN]?.trim() == SCORE_HEADER && row[POPULATION_COLUMN]?.trim() == POPULATION_HEADER

    private sealed interface Row {
        data class Suburb(val value: SeifaSuburb) : Row
        data object NoScore : Row
        data object Other : Row
    }

    private fun parse(row: Map<Int, String>): Row {
        val code = row[CODE_COLUMN]?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return Row.Other
        if (!code.startsWith(NSW_CODE_PREFIX)) return Row.Other
        val name = row[NAME_COLUMN]?.trim().orEmpty()
        val score = row[IRSAD_SCORE_COLUMN]?.toDoubleOrNull()
        val decile = decile(row, IRSAD_DECILE_COLUMN)
        val population = row[POPULATION_COLUMN]?.toDoubleOrNull()?.toInt() ?: 0
        if (name.isEmpty() || score == null || decile == null) return Row.NoScore
        return Row.Suburb(
            SeifaSuburb(
                SAL_PREFIX + code, name, score, decile, population.coerceAtLeast(0),
                irsdDecile = decile(row, IRSD_DECILE_COLUMN), ierDecile = decile(row, IER_DECILE_COLUMN), ieoDecile = decile(row, IEO_DECILE_COLUMN),
            ),
        )
    }

    /** A decile from 1 to 10, or null when the cell is empty or not one (the ABS leaves the score of an excluded area blank). */
    private fun decile(row: Map<Int, String>, column: Int): Int? = row[column]?.toDoubleOrNull()?.toInt()?.takeIf { it in MIN_DECILE..MAX_DECILE }
}

/** The tool's output: a suburb's name with its IRSAD score, decile and population, read by `pipeline/sql/seifa.sql`. */
object SeifaCsv {
    const val HEADER: String = "sal_code,sal_name,irsad_score,irsad_decile,population,irsd_decile,ier_decile,ieo_decile"

    fun format(suburbs: List<SeifaSuburb>): List<String> =
        listOf(HEADER) + suburbs.map { listOf(it.salCode, quoted(it.name), it.irsadScore, it.irsadDecile, it.population, it.irsdDecile ?: "", it.ierDecile ?: "", it.ieoDecile ?: "").joinToString(",") }

    private fun quoted(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""
}
