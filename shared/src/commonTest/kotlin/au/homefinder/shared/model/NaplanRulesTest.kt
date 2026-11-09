package au.homefinder.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NaplanRulesTest {
    private fun check(score: Int) = NaplanRules.check(NaplanEntry(score))

    @Test
    fun `differences from -1000 to +1000 are accepted`() {
        listOf(-41, 25, 0, -1000, 1000).forEach { assertEquals(NaplanCheck.Valid(NaplanEntry(it)), check(it), "$it") }
    }

    @Test
    fun `a difference outside -1000 to +1000 is refused`() {
        listOf(-1001, 1001, Int.MAX_VALUE, Int.MIN_VALUE).forEach {
            assertEquals(NaplanCheck.Invalid(NaplanProblem.SCORE_OUT_OF_RANGE), check(it), "$it")
        }
    }

    @Test
    fun `the label shows the difference with its sign and no year`() {
        fun label(points: Int) = NaplanRules.label(NaplanScore(points, LocalDate(2026, 11, 8)))
        assertEquals("NAPLAN -41 vs national", label(-41))
        assertEquals("NAPLAN +25 vs national", label(25))
        assertEquals("NAPLAN 0 vs national", label(0))
    }

    @Test
    fun `a year or note sent by an old client is ignored (ADR 0073)`() {
        assertEquals(NaplanEntry(-41), ApiJson.decodeFromString<NaplanEntry>("""{"score":-41}"""))
        assertEquals(NaplanEntry(-41), ApiJson.decodeFromString<NaplanEntry>("""{"score":-41,"year":2025,"note":"old"}"""))
        assertEquals(NaplanEntry(-41), ApiJson.decodeFromString<NaplanEntry>("""{"score":-41,"year":null}"""))
    }

    @Test
    fun `a fractional difference is not a whole number of points, so it does not decode`() {
        assertFailsWith<IllegalArgumentException> { ApiJson.decodeFromString<NaplanEntry>("""{"score":-41.5}""") }
    }

    @Test
    fun `a school without a figure round-trips as before, and one with a figure keeps it`() {
        val plain = School("1", "Hamilton Public School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, 1051)
        assertNull(ApiJson.decodeFromString<School>(ApiJson.encodeToString(School.serializer(), plain)).naplan)
        val scored = plain.copy(naplan = NaplanScore(512, LocalDate(2026, 11, 8)))
        assertEquals(scored, ApiJson.decodeFromString<School>(ApiJson.encodeToString(School.serializer(), scored)))
    }
}
