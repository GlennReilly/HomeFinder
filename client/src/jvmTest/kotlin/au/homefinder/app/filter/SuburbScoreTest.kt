package au.homefinder.app.filter

import au.homefinder.app.ui.scoreRankingLines
import au.homefinder.app.ui.scoreSummaryText
import au.homefinder.shared.model.FilterProvenance
import au.homefinder.shared.model.SuburbFilterValue
import au.homefinder.shared.model.SuburbFiltersResponse
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuburbScoreTest {
    private val day = LocalDate(2026, 10, 2)
    private val data = FilterData.from(
        SuburbFiltersResponse(
            listOf(
                SuburbFilterValue("a", averageIcsea = 1100, beachKm = 1.0, name = "Alpha"),
                SuburbFilterValue("b", averageIcsea = 1000, beachKm = 11.0, name = "Beta"),
                SuburbFilterValue("c", averageIcsea = 1050, beachKm = null, name = "Gamma"),
                SuburbFilterValue("d", averageIcsea = null, beachKm = null, name = "Delta"),
            ),
            "Schools", day, beach = FilterProvenance("OSM", day),
        ),
    )

    private fun setup(vararg pairs: Pair<FilterCriterion, ScoreWeight>) = ScoreSetup(mapOf(*pairs))
    private val icsea = FilterCriterion.ICSEA to ScoreWeight(1, Preference.HIGHER)
    private val beach = FilterCriterion.BEACH to ScoreWeight(1, Preference.LOWER)

    @Test
    fun `a single criterion scales from the worst suburb at 0 to the best at 100`() {
        val result = data.score(setup(icsea))
        assertEquals(mapOf("a" to 100, "b" to 0, "c" to 50), result.scores)
        assertEquals(listOf("a", "c", "b"), result.ranking.map { it.suburbId })
        assertEquals(1, result.unscored, "'d' has no ICSEA")
    }

    @Test
    fun `lower is better flips the scale`() {
        assertEquals(mapOf("a" to 100, "b" to 0), data.score(setup(beach)).scores)
        assertEquals(mapOf("a" to 0, "b" to 100), data.score(setup(FilterCriterion.BEACH to ScoreWeight(3, Preference.HIGHER))).scores)
    }

    @Test
    fun `weights decide how much each criterion counts`() {
        val even = data.score(setup(icsea, beach))
        assertEquals(100, even.scores.getValue("a"))
        // 'c' has no beach distance, so it is scored on ICSEA alone and marked partial.
        assertEquals(50, even.scores.getValue("c"))
        assertTrue(even.ranking.first { it.suburbId == "c" }.partial)
        assertFalse(even.ranking.first { it.suburbId == "a" }.partial)
        val heavy = data.score(setup(icsea, FilterCriterion.BEACH to ScoreWeight(3, Preference.LOWER)))
        assertEquals(100, heavy.scores.getValue("a"))
        assertEquals(0, heavy.scores.getValue("b"))
    }

    @Test
    fun `a weight of zero is ignored, and with no weights there is no score`() {
        assertEquals(data.score(setup(icsea)).scores, data.score(setup(icsea, FilterCriterion.BEACH to ScoreWeight(0, Preference.LOWER))).scores)
        val none = data.score(ScoreSetup())
        assertTrue(none.ranking.isEmpty())
        assertEquals(4, none.unscored)
    }

    @Test
    fun `a criterion with no data is ignored and equal values score in the middle`() {
        assertEquals(data.score(setup(icsea)).scores, data.score(setup(icsea, FilterCriterion.RENTED to ScoreWeight(5, Preference.LOWER))).scores)
        val flat = FilterData.from(SuburbFiltersResponse(listOf(SuburbFilterValue("x", 1000), SuburbFilterValue("y", 1000)), "S", day))
        assertEquals(mapOf("x" to 50, "y" to 50), flat.score(setup(icsea)).scores)
    }

    @Test
    fun `ties are ordered by name and a name falls back to the id`() {
        val tie = FilterData.from(SuburbFiltersResponse(listOf(SuburbFilterValue("2", 1000, name = "Zed"), SuburbFilterValue("1", 1000, name = "Abe"), SuburbFilterValue("3", 1000)), "S", day))
        assertEquals(listOf("3", "Abe", "Zed"), tie.score(setup(icsea)).ranking.map { it.name })
    }

    @Test
    fun `a weight above the maximum is refused`() {
        assertFailsWith<IllegalArgumentException> { ScoreWeight(ScoreWeight.MAX_WEIGHT + 1, Preference.HIGHER) }
    }

    @Test
    fun `the ranking reads best first and marks partial scores`() {
        val result = data.score(setup(icsea, beach))
        assertEquals(listOf("1. Alpha, 100", "2. Gamma, 50 (partial)", "3. Beta, 0"), scoreRankingLines(result))
        assertEquals(listOf("1. Alpha, 100"), scoreRankingLines(result, limit = 1))
        assertEquals("3 suburbs scored, 1 with no values to score.", scoreSummaryText(result))
        assertEquals("Give a criterion a weight above 0 to score suburbs.", scoreSummaryText(data.score(ScoreSetup())))
    }

    @Test
    fun `each criterion starts on the end most people would call better`() {
        assertEquals(Preference.HIGHER, FilterCriterion.ICSEA.defaultPreference)
        assertEquals(Preference.LOWER, FilterCriterion.BEACH.defaultPreference)
    }
}
