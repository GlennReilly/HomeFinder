package au.homefinder.census

import java.io.BufferedReader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.util.Locale

/** One suburb's recorded incidents over the latest [Crime.WINDOW_MONTHS] months for the two measures (ADR 0051). */
data class SuburbCrime(val name: String, val violent: Int, val property: Int)

/** What [Crime.read] found: the suburbs, and the months they cover (first and last month of the window, and the day the data runs to). */
sealed interface CrimeResult {
    data class Read(val suburbs: List<SuburbCrime>, val firstMonth: YearMonth, val lastMonth: YearMonth) : CrimeResult {
        val windowText: String get() = "${label(firstMonth)} to ${label(lastMonth)}"
        val asOf: LocalDate get() = lastMonth.atEndOfMonth()

        private fun label(month: YearMonth) = month.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " " + month.year
    }

    data class Failed(val reason: String) : CrimeResult
}

/**
 * Reads the BOCSAR file `SuburbData.csv` (recorded criminal incidents by suburb, offence and month, ADR 0051), streaming it line by line because it
 * is some 400 MB. Columns: "Suburb", "Offence category", "Subcategory", then one count per month headed like "Jun 2026". The latest [WINDOW_MONTHS]
 * columns are summed for each of the two measures.
 */
object Crime {
    const val WINDOW_MONTHS: Int = 24
    private const val FIXED_COLUMNS = 3
    private const val SUBURB_COLUMN = 0
    private const val CATEGORY_COLUMN = 1
    private const val SUBCATEGORY_COLUMN = 2

    /** Violence against the person that residents notice: every assault, robbery and sexual offence subcategory. */
    val VIOLENT: Set<Pair<String, String>> = setOf(
        "Assault" to "Domestic violence related assault",
        "Assault" to "Non-domestic violence related assault",
        "Assault" to "Assault Police",
        "Robbery" to "Robbery without a weapon",
        "Robbery" to "Robbery with a firearm",
        "Robbery" to "Robbery with a weapon not a firearm",
        "Sexual offences" to "Sexual assault",
        "Sexual offences" to "Sexual touching, sexual act and other sexual offences",
    )

    /** What happens to homes, cars and property: break-ins, vehicle theft, stealing from cars and dwellings, and malicious damage. */
    val PROPERTY: Set<Pair<String, String>> = setOf(
        "Theft" to "Break and enter dwelling",
        "Theft" to "Motor vehicle theft",
        "Theft" to "Steal from motor vehicle",
        "Theft" to "Steal from dwelling",
        "Malicious damage to property" to "Malicious damage to property",
    )

    fun read(file: Path): CrimeResult = try {
        Files.newBufferedReader(file).use { read(it) }
    } catch (failure: IOException) {
        CrimeResult.Failed("Could not read $file: ${failure.message}")
    }

    internal fun read(reader: BufferedReader): CrimeResult {
        val header = reader.readLine()?.let(::parseLine) ?: return CrimeResult.Failed("The file is empty")
        if (header.size < FIXED_COLUMNS + WINDOW_MONTHS || header[SUBURB_COLUMN] != "Suburb") {
            return CrimeResult.Failed("Not the BOCSAR suburb file: expected \"Suburb\", \"Offence category\", \"Subcategory\" and at least $WINDOW_MONTHS month columns")
        }
        val months = header.takeLast(WINDOW_MONTHS).map { parseMonth(it) ?: return CrimeResult.Failed("Month column \"$it\" is not like \"Jun 2026\"") }
        if (months.zipWithNext().any { (a, b) -> a.plusMonths(1) != b }) return CrimeResult.Failed("The last $WINDOW_MONTHS month columns are not consecutive months")
        val violent = LinkedHashMap<String, Int>()
        val property = HashMap<String, Int>()
        val firstWindowColumn = header.size - WINDOW_MONTHS
        reader.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val cells = parseLine(line)
            if (cells.size != header.size) return CrimeResult.Failed("A row for \"${cells.firstOrNull()}\" has ${cells.size} cells, the header ${header.size}")
            val suburb = cells[SUBURB_COLUMN]
            violent.putIfAbsent(suburb, 0)
            val key = cells[CATEGORY_COLUMN] to cells[SUBCATEGORY_COLUMN].trim()
            val target = when (key) {
                in VIOLENT -> violent
                in PROPERTY -> property
                else -> return@forEach
            }
            target[suburb] = (target[suburb] ?: 0) + (firstWindowColumn until cells.size).sumOf { cells[it].trim().toIntOrNull() ?: 0 }
        }
        if (violent.isEmpty()) return CrimeResult.Failed("The file has no suburbs")
        return CrimeResult.Read(violent.map { (name, v) -> SuburbCrime(name, v, property[name] ?: 0) }, months.first(), months.last())
    }

    /** "Jun 2026" to a month; null when it is not one. */
    internal fun parseMonth(text: String): YearMonth? {
        val parts = text.trim().split(' ')
        if (parts.size != 2) return null
        val month = Month.entries.firstOrNull { it.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).equals(parts[0], ignoreCase = true) } ?: return null
        val year = parts[1].toIntOrNull() ?: return null
        return YearMonth.of(year, month)
    }

    /** One CSV line: cells in double quotes (a comma inside one stays inside), `""` for a quote; unquoted cells are accepted too. */
    internal fun parseLine(line: String): List<String> {
        val cells = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { current.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { cells += current.toString(); current.setLength(0) }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return cells
    }
}

/** The tool's output: a suburb's name with its two incident counts and the window they cover, read by `pipeline/sql/crime.sql`. */
object CrimeCsv {
    const val HEADER: String = "bocsar_name,violent,property,window_text,as_of"

    fun format(result: CrimeResult.Read): List<String> =
        listOf(HEADER) + result.suburbs.map { listOf(quoted(it.name), it.violent, it.property, quoted(result.windowText), result.asOf).joinToString(",") }

    private fun quoted(text: String): String = "\"" + text.replace("\"", "\"\"") + "\""
}
