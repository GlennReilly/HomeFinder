package au.homefinder.server

/**
 * What was typed in the search box, reduced to words. Words are lower-case letters and digits, so punctuation, a slash in
 * "3/12" and stray characters all just separate words, and nothing typed can reach the database as syntax: only these
 * words are ever turned into a text-search query (ADR 0026).
 */
class SearchQuery private constructor(val words: List<String>) {
    val isEmpty: Boolean get() = words.isEmpty()

    /** The query as the address text would read it, with fully typed street-type abbreviations spelled out. */
    val normalised: String get() = words.map { STREET_TYPES[it] ?: it }.joinToString(" ")

    /**
     * A PostgreSQL `to_tsquery` string: every word must begin a word in the address. A word that is a street-type
     * abbreviation ("st") also matches the full word ("street"), whichever the address holds.
     */
    fun tsQuery(): String = words.joinToString(" & ") { word ->
        val full = STREET_TYPES[word]
        if (full == null) "$word:*" else "($word:* | $full:*)"
    }

    val startsWithDigit: Boolean get() = words.firstOrNull()?.first()?.isDigit() == true

    companion object {
        const val MAX_WORDS: Int = 8
        private val WORD = Regex("[a-z0-9]+")

        /** Abbreviations seen in street addresses mapped to the full street types G-NAF stores. */
        val STREET_TYPES: Map<String, String> = mapOf(
            "st" to "street", "rd" to "road", "ave" to "avenue", "av" to "avenue", "dr" to "drive", "drv" to "drive",
            "cres" to "crescent", "ct" to "court", "pl" to "place", "ln" to "lane", "cl" to "close", "tce" to "terrace",
            "pde" to "parade", "hwy" to "highway", "blvd" to "boulevard", "cct" to "circuit", "esp" to "esplanade",
            "gr" to "grove", "pwy" to "parkway", "sq" to "square",
        )

        fun parse(text: String): SearchQuery = SearchQuery(WORD.findAll(text.lowercase()).map { it.value }.take(MAX_WORDS).toList())
    }
}
