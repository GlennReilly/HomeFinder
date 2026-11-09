package au.homefinder.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NaplanRulesTest {
    private val thisYear = 2026

    private fun check(score: Int, year: Int) = NaplanRules.check(NaplanEntry(score, year), thisYear)

    @Test
    fun `differences from -1000 to +1000 and years from 2008 to this year are accepted`() {
        listOf(-41 to 2025, 25 to 2025, 0 to 2008, -1000 to 2024, 1000 to thisYear).forEach { (score, year) ->
            assertEquals(NaplanCheck.Valid(NaplanEntry(score, year)), check(score, year), "$score in $year")
        }
    }

    @Test
    fun `a difference outside -1000 to +1000 is refused`() {
        listOf(-1001, 1001, Int.MAX_VALUE, Int.MIN_VALUE).forEach {
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
    fun `the label shows the difference with its sign and the year`() {
        fun label(points: Int) = NaplanRules.label(NaplanScore(points, 2025, LocalDate(2026, 11, 8)))
        assertEquals("NAPLAN -41 vs national (2025)", label(-41))
        assertEquals("NAPLAN +25 vs national (2025)", label(25))
        assertEquals("NAPLAN 0 vs national (2025)", label(0))
        assertEquals("NAPLAN -41 vs national", NaplanRules.label(NaplanScore(-41, null)), "no year, no bracket (ADR 0071)")
    }

    @Test
    fun `the year may be left out, but a given year is still checked, and a note sent by an old client is ignored`() {
        assertEquals(NaplanCheck.Valid(NaplanEntry(-41, null)), NaplanRules.check(NaplanEntry(-41, null), thisYear))
        assertEquals(NaplanCheck.Invalid(NaplanProblem.YEAR_OUT_OF_RANGE), NaplanRules.check(NaplanEntry(-41, 2007), thisYear))
        assertEquals(NaplanEntry(-41, null), ApiJson.decodeFromString<NaplanEntry>("""{"score":-41}"""))
        assertEquals(NaplanEntry(-41, 2025), ApiJson.decodeFromString<NaplanEntry>("""{"score":-41,"year":2025,"note":"old"}"""))
    }

    @Test
    fun `a fractional difference is not a whole number of points, so it does not decode`() {
        assertFailsWith<IllegalArgumentException> { ApiJson.decodeFromString<NaplanEntry>("""{"score":-41.5,"year":2025}""") }
    }

    @Test
    fun `a school without a figure round-trips as before, and one with a figure keeps it`() {
        val plain = School("1", "Hamilton Public School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, 1051)
        assertNull(ApiJson.decodeFromString<School>(ApiJson.encodeToString(School.serializer(), plain)).naplan)
        val scored = plain.copy(naplan = NaplanScore(512, 2025, LocalDate(2026, 11, 8)))
        assertEquals(scored, ApiJson.decodeFromString<School>(ApiJson.encodeToString(School.serializer(), scored)))
    }
}
