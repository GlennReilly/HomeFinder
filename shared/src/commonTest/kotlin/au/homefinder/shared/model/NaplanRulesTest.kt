package au.homefinder.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaplanRulesTest {
    private val thisYear = 2026

    private fun check(score: Int, year: Int, note: String? = null) = NaplanRules.check(NaplanEntry(score, year, note), thisYear)

    @Test
    fun `scores from 0 to 1000 and years from 2008 to this year are accepted`() {
        listOf(0 to 2008, 1000 to thisYear, 512 to 2025).forEach { (score, year) ->
            assertEquals(NaplanCheck.Valid(NaplanEntry(score, year)), check(score, year), "$score in $year")
        }
    }

    @Test
    fun `a score outside 0 to 1000 is refused`() {
        listOf(-1, 1001, Int.MAX_VALUE, Int.MIN_VALUE).forEach {
            assertEquals(NaplanCheck.Invalid(NaplanProblem.SCORE_OUT_OF_RANGE), check(it, 2025), "$it")
        }
    }

    @Test
    fun `a year before NAPLAN began or after this year is refused`() {
        listOf(2007, thisYear + 1, 0, 25).forEach {
            assertEquals(NaplanCheck.Invalid(NaplanProblem.YEAR_OUT_OF_RANGE), check(512, it), "$it")
        }
    }

    @Test
    fun `the note is trimmed, a blank one is dropped and a long one is refused`() {
        assertEquals(NaplanCheck.Valid(NaplanEntry(512, 2025, "Year 5 reading")), check(512, 2025, "  Year 5 reading "))
        assertEquals(NaplanCheck.Valid(NaplanEntry(512, 2025, null)), check(512, 2025, "   "))
        assertEquals(NaplanCheck.Valid(NaplanEntry(512, 2025, "x".repeat(NaplanRules.MAX_NOTE_LENGTH))), check(512, 2025, "x".repeat(NaplanRules.MAX_NOTE_LENGTH)))
        assertEquals(NaplanCheck.Invalid(NaplanProblem.NOTE_TOO_LONG), check(512, 2025, "x".repeat(NaplanRules.MAX_NOTE_LENGTH + 1)))
    }

    @Test
    fun `the label shows the score and its year`() {
        assertEquals("NAPLAN 512 (2025)", NaplanRules.label(NaplanScore(512, 2025, "note", LocalDate(2026, 11, 8))))
    }

    @Test
    fun `a school without a figure round-trips as before, and one with a figure keeps it`() {
        val plain = School("1", "Hamilton Public School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, 1051)
        assertNull(ApiJson.decodeFromString<School>(ApiJson.encodeToString(School.serializer(), plain)).naplan)
        val scored = plain.copy(naplan = NaplanScore(512, 2025, null, LocalDate(2026, 11, 8)))
        assertEquals(scored, ApiJson.decodeFromString<School>(ApiJson.encodeToString(School.serializer(), scored)))
    }
}
