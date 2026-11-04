package au.homefinder.census

/** The tool's output: a suburb's name with its counts, read by `pipeline/sql/census.sql`. */
object CensusCsv {
    const val HEADER: String = "sal_code,sal_name,tenure_stated,rented,houses,semis,flats,other_dwellings"

    /** Suburbs with no name in the workbook are dropped (they cannot be matched); the count dropped is returned beside the lines. */
    fun format(suburbs: List<TenureDwellings>, namesByCode: Map<String, String>): Pair<List<String>, Int> {
        val named = suburbs.filter { it.salCode in namesByCode }
        val lines = named.map {
            listOf(it.salCode, quoted(namesByCode.getValue(it.salCode)), it.tenureStated, it.rented, it.houses, it.semis, it.flats, it.otherDwellings)
                .joinToString(",")
        }
        return (listOf(HEADER) + lines) to (suburbs.size - named.size)
    }

    /** Names with a comma or a quote (none today) would break the row, so every name is quoted. */
    private fun quoted(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""
}
