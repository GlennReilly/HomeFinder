package au.homefinder.census

/**
 * One suburb's counts from Census table G37 (tenure and landlord type by dwelling structure, occupied private dwellings), 2021.
 * Plain counts: the shares and what they are worth are worked out where they are shown (ADR 0044). The ABS randomly adjusts small counts,
 * so a table's totals can differ from the sum of its parts by a few dwellings; the bases here are chosen so the shares stay sensible.
 */
data class TenureDwellings(
    val salCode: String,
    /** Occupied private dwellings whose tenure was stated: the base for the share rented (never below [rented]). */
    val tenureStated: Int,
    val rented: Int,
    val houses: Int,
    /** Semi-detached, row or terrace houses and townhouses. */
    val semis: Int,
    /** Flats, units and apartments. */
    val flats: Int,
    val otherDwellings: Int,
) {
    init {
        require(rented >= 0 && tenureStated >= rented) { "Rented dwellings cannot exceed those with a stated tenure" }
        require(houses >= 0 && semis >= 0 && flats >= 0 && otherDwellings >= 0) { "Dwelling counts cannot be negative" }
    }

    /** The base for the mix of structures: the four stated structures added up, so the shares always add to a whole. */
    val structureStated: Int get() = houses + semis + flats + otherDwellings
}

/** What [G37.parse] made of the file. */
sealed interface G37Result {
    data class Parsed(val suburbs: List<TenureDwellings>) : G37Result
    data class Malformed(val lineNumber: Int, val reason: String) : G37Result
}

/** Reads `2021Census_G37_NSW_SAL.csv`: one row per suburb, columns found by their names in the header. */
object G37 {
    const val CODE_COLUMN: String = "SAL_CODE_2021"
    const val RENTED_TOTAL: String = "R_Tot_Total"
    const val ALL_TOTAL: String = "Total_Total"
    const val TENURE_NOT_STATED_TOTAL: String = "Ten_type_NS_Total"
    const val HOUSES: String = "Total_DS_Sep_house"
    const val SEMIS: String = "Total_DS_SemiD_ro_or_tce_h_th"
    const val FLATS: String = "Total_DS_Flat_apart"
    const val OTHER_DWELLINGS: String = "Total_DS_Oth_dwell"
    private val NEEDED = listOf(CODE_COLUMN, RENTED_TOTAL, ALL_TOTAL, TENURE_NOT_STATED_TOTAL, HOUSES, SEMIS, FLATS, OTHER_DWELLINGS)

    fun parse(lines: List<String>): G37Result {
        val header = lines.firstOrNull()?.trim()?.split(',')
            ?: return G37Result.Malformed(1, "The file is empty")
        val at = NEEDED.associateWith { header.indexOf(it) }
        at.filterValues { it < 0 }.keys.firstOrNull()?.let { return G37Result.Malformed(1, "The header has no $it column; the table has changed") }
        val suburbs = ArrayList<TenureDwellings>(lines.size - 1)
        for ((index, raw) in lines.withIndex().drop(1)) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val fields = line.split(',')
            val lineNumber = index + 1
            if (fields.size != header.size) return G37Result.Malformed(lineNumber, "Expected ${header.size} fields but found ${fields.size}")
            fun count(name: String): Int? = fields[at.getValue(name)].trim().toIntOrNull()?.takeIf { it >= 0 }
            val values = NEEDED.drop(1).associateWith { count(it) }
            values.filterValues { it == null }.keys.firstOrNull()?.let { return G37Result.Malformed(lineNumber, "$it is not a count: $line") }
            val rented = values.getValue(RENTED_TOTAL)!!
            val tenureStated = maxOf(values.getValue(ALL_TOTAL)!! - values.getValue(TENURE_NOT_STATED_TOTAL)!!, rented)
            suburbs += TenureDwellings(
                salCode = fields[at.getValue(CODE_COLUMN)].trim(),
                tenureStated = tenureStated, rented = rented,
                houses = values.getValue(HOUSES)!!, semis = values.getValue(SEMIS)!!, flats = values.getValue(FLATS)!!,
                otherDwellings = values.getValue(OTHER_DWELLINGS)!!,
            )
        }
        return G37Result.Parsed(suburbs)
    }
}
