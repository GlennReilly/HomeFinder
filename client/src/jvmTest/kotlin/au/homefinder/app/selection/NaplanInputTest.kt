package au.homefinder.app.selection

import au.homefinder.shared.model.NaplanEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class NaplanInputTest {
    private val thisYear = 2026

    private fun parse(score: String, year: String, note: String = "") = NaplanInput.parse(score, year, note, thisYear)

    @Test
    fun `a whole score and a year are accepted, with spaces trimmed and the note kept`() {
        assertEquals(ParsedNaplan.Valid(NaplanEntry(512, 2025, null)), parse(" 512 ", "2025"))
        assertEquals(ParsedNaplan.Valid(NaplanEntry(0, 2008, "Year 5 reading")), parse("0", "2008", " Year 5 reading "))
        assertEquals(ParsedNaplan.Valid(NaplanEntry(1000, 2026, null)), parse("1000", "2026", "   "))
    }

    @Test
    fun `a missing score or year says what to type`() {
        assertEquals(ParsedNaplan.Invalid(NaplanInput.MESSAGE_SCORE_MISSING), parse("  ", "2025"))
        assertEquals(ParsedNaplan.Invalid(NaplanInput.MESSAGE_YEAR_MISSING), parse("512", ""))
    }

    @Test
    fun `a score that is not a whole number from 0 to 1000 is refused`() {
        listOf("1001", "-1", "512.5", "abc", "5 12", "99999999999").forEach {
            assertEquals(ParsedNaplan.Invalid(NaplanInput.MESSAGE_SCORE_RANGE), parse(it, "2025"), it)
        }
    }

    @Test
    fun `a year before 2008, after this year or not a number is refused`() {
        listOf("2007", "2027", "25x", "-2025", "99999999999").forEach {
            assertEquals(ParsedNaplan.Invalid(NaplanInput.MESSAGE_YEAR_RANGE), parse("512", it), it)
        }
    }

    @Test
    fun `a note over 200 characters is refused`() {
        assertEquals(ParsedNaplan.Invalid(NaplanInput.MESSAGE_NOTE_LONG), parse("512", "2025", "x".repeat(201)))
    }
}
