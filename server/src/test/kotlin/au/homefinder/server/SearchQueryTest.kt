package au.homefinder.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchQueryTest {
    @Test
    fun `words are lower-case letters and digits and everything else separates them`() {
        assertEquals(listOf("3", "12", "main", "st"), SearchQuery.parse("3/12 Main St").words)
        assertEquals(listOf("o", "connell", "street"), SearchQuery.parse("  O'Connell, Street!! ").words)
        assertEquals(listOf("12a", "high", "st"), SearchQuery.parse("12A HIGH-ST").words)
    }

    @Test
    fun `punctuation only or empty text has no words`() {
        assertTrue(SearchQuery.parse("").isEmpty)
        assertTrue(SearchQuery.parse("  ' ; -- ").isEmpty)
    }

    @Test
    fun `at most eight words are used`() {
        assertEquals(8, SearchQuery.parse("a b c d e f g h i j k").words.size)
    }

    @Test
    fun `the text-search query makes every word a prefix and joins them with and`() {
        assertEquals("12:* & main:* & ham:*", SearchQuery.parse("12 main ham").tsQuery())
    }

    @Test
    fun `a street-type abbreviation also matches the full word`() {
        assertEquals("12:* & main:* & (st:* | street:*)", SearchQuery.parse("12 main st").tsQuery())
        assertEquals("(rd:* | road:*)", SearchQuery.parse("rd").tsQuery())
    }

    @Test
    fun `the normalised text spells out abbreviations the way the address reads`() {
        assertEquals("3 12 main street", SearchQuery.parse("3/12 main st").normalised)
        assertEquals("12 main street hamilton", SearchQuery.parse("12 Main Street Hamilton").normalised)
    }

    @Test
    fun `nothing typed can add text-search syntax`() {
        val query = SearchQuery.parse("a' | b & !c:* <-> (d) \\ ; DROP TABLE address")
        assertFalse(query.tsQuery().any { it in "'\u005C;<>!" }, query.tsQuery())
        assertTrue(query.words.all { word -> word.all { it.isLetterOrDigit() } })
    }

    @Test
    fun `a leading digit is detected`() {
        assertTrue(SearchQuery.parse("12 main").startsWithDigit)
        assertFalse(SearchQuery.parse("main 12").startsWithDigit)
    }
}
