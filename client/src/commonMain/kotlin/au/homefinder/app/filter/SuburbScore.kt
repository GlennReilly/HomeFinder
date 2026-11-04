package au.homefinder.app.filter

import kotlin.math.roundToInt

/** Which end of a criterion is better: a high ICSEA, a short walk to the beach. */
enum class Preference { HIGHER, LOWER }

/** Where a criterion starts in the score panel: the end most people would call better. */
val FilterCriterion.defaultPreference: Preference
    get() = when (this) {
        FilterCriterion.ICSEA, FilterCriterion.PARKS -> Preference.HIGHER
        FilterCriterion.BEACH, FilterCriterion.RENTED -> Preference.LOWER
    }

/** How much one criterion counts in the score (0 is ignored) and which end is better (ADR 0047). */
data class ScoreWeight(val weight: Int, val preference: Preference) {
    init {
        require(weight in 0..MAX_WEIGHT) { "weight $weight is outside 0..$MAX_WEIGHT" }
    }

    companion object {
        const val MAX_WEIGHT: Int = 5
    }
}

/** What the score panel has set. A criterion with weight 0 is ignored; with none above 0 there is no score. */
data class ScoreSetup(val weights: Map<FilterCriterion, ScoreWeight> = emptyMap()) {
    val active: Map<FilterCriterion, ScoreWeight> get() = weights.filterValues { it.weight > 0 }
    val isActive: Boolean get() = active.isNotEmpty()
}

/** One suburb's score from 0 (worst of the suburbs on every weighted criterion) to 100 (best); [partial] when a weighted value was unknown. */
data class SuburbScore(val suburbId: String, val name: String, val score: Int, val partial: Boolean)

/** The scored suburbs, best first (ties by name), and how many had no value for any weighted criterion. */
data class ScoreResult(val setup: ScoreSetup, val ranking: List<SuburbScore>, val unscored: Int) {
    val scores: Map<String, Int> get() = ranking.associate { it.suburbId to it.score }
}

internal object ScoreCalculator {
    private const val FULL = 100.0

    /**
     * Each weighted criterion is scaled across the suburbs that have a value (lowest to highest, flipped when lower is better),
     * and a suburb's score is the weighted average of the criteria it has a value for. A criterion nobody has a value for is ignored.
     */
    fun score(
        setup: ScoreSetup,
        values: Map<FilterCriterion, Map<String, Double?>>,
        suburbIds: Set<String>,
        nameOf: (String) -> String,
    ): ScoreResult {
        val scales = setup.active.mapNotNull { (criterion, weight) ->
            val known = values[criterion].orEmpty().values.filterNotNull()
            if (known.isEmpty()) null else Triple(criterion, weight, known.min() to known.max())
        }
        if (scales.isEmpty()) return ScoreResult(setup, emptyList(), suburbIds.size)
        val ranking = suburbIds.mapNotNull { id ->
            var weighted = 0.0
            var total = 0
            var missing = false
            scales.forEach { (criterion, weight, range) ->
                val value = values[criterion]?.get(id)
                if (value == null) {
                    missing = true
                } else {
                    val spread = range.second - range.first
                    val scaled = if (spread == 0.0) 0.5 else (value - range.first) / spread
                    weighted += weight.weight * if (weight.preference == Preference.HIGHER) scaled else 1.0 - scaled
                    total += weight.weight
                }
            }
            if (total == 0) null else SuburbScore(id, nameOf(id), (weighted / total * FULL).roundToInt(), missing)
        }.sortedWith(compareByDescending<SuburbScore> { it.score }.thenBy { it.name })
        return ScoreResult(setup, ranking, suburbIds.size - ranking.size)
    }
}
