package au.homefinder.app.selection

import au.homefinder.shared.model.NaplanEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class NaplanInputTest {
    private val thisYear = 2026

    private fun parse(score: String, year: String) = NaplanInput.parse(score, year, thisYear)

    @Test
    fun `a signed whole difference and a year are accepted, with spaces trimmed`() {
        assertEquals(ParsedNaplan.Valid(NaplanEntry(-41, 2025)), parse(" -41 ", "2025"))
        assertEquals(ParsedNaplan.Valid(NaplanEntry(25, 2025)), parse("+25", "2025"))
        assertEquals(ParsedNaplan.Valid(NaplanEntry(-41, 2025)), parse("\u221241", "2025"), "a typographic minus as other sites print it")
        assertEquals(ParsedNaplan.Valid(NaplanEntry(-1000, 2025)), parse("-1000", "2025"))
        assertEquals(ParsedNaplan.Valid(NaplanEntry(0, 2008)), parse("0", " 2008 "))
        assertEquals(ParsedNaplan.Valid(NaplanEntry(1000, 2026)), parse("1000", "2026"))
    }

    @Test
    fun `a missing score says what to type, and the year may be left empty`() {
        assertEquals(ParsedNaplan.Invalid(NaplanInput.MESSAGE_SCORE_MISSING), parse("  ", "2025"))
        assertEquals(ParsedNaplan.Valid(NaplanEntry(-41, null)), parse("-41", ""))
        assertEquals(ParsedNaplan.Valid(NaplanEntry(-41, null)), parse("-41", "   "))
    }

    @Test
    fun `a difference that is not a whole number from -1000 to +1000 is refused`() {
        listOf("1001", "-1001", "-41.5", "abc", "5 12", "--41", "+-4", "-", "99999999999").forEach {
            assertEquals(ParsedNaplan.Invalid(NaplanInput.MESSAGE_SCORE_RANGE), parse(it, "2025"), it)
        }
    }

    @Test
    fun `a year before 2008, after this year or not a number is refused`() {
        listOf("2007", "2027", "25x", "-2025", "99999999999").forEach {
            assertEquals(ParsedNaplan.Invalid(NaplanInput.MESSAGE_YEAR_RANGE), parse("512", it), it)
        }
    }

}
