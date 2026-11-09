package au.homefinder.app.selection

import au.homefinder.shared.model.NaplanCheck
import au.homefinder.shared.model.NaplanEntry
import au.homefinder.shared.model.NaplanRules

/** What typing into the NAPLAN form came to: a valid entry, or the words to show beside the form. */
sealed interface ParsedNaplan {
    data class Valid(val entry: NaplanEntry) : ParsedNaplan
    data class Invalid(val message: String) : ParsedNaplan
}

/** Turns the box of the NAPLAN form into a [NaplanEntry], or says what is wrong before anything is sent (ADR 0068, 0069). */
object NaplanInput {
    const val MESSAGE_SCORE_MISSING: String = "Enter the difference from the national average, for example -41"
    const val MESSAGE_SCORE_RANGE: String = "The difference must be a whole number from ${NaplanRules.MIN_SCORE} to +${NaplanRules.MAX_SCORE}"

    /** A whole number of points with an optional sign: "-41", "+25", "0". */
    private val SIGNED_WHOLE_NUMBER = Regex("[+-]?[0-9]{1,6}")

    /** Minus signs as other sites print them (the typographic minus and dashes), read as an ordinary "-". */
    private val MINUS_LOOKALIKES = Regex("[\u2212\u2013\u2014]")
    private const val PLUS = "+"

    fun parse(scoreText: String): ParsedNaplan {
        val score = MINUS_LOOKALIKES.replace(scoreText.trim(), "-")
        if (score.isEmpty()) return ParsedNaplan.Invalid(MESSAGE_SCORE_MISSING)
        if (!SIGNED_WHOLE_NUMBER.matches(score)) return ParsedNaplan.Invalid(MESSAGE_SCORE_RANGE)
        return when (NaplanRules.check(NaplanEntry(score.removePrefix(PLUS).toInt()))) {
            is NaplanCheck.Valid -> ParsedNaplan.Valid(NaplanEntry(score.removePrefix(PLUS).toInt()))
            is NaplanCheck.Invalid -> ParsedNaplan.Invalid(MESSAGE_SCORE_RANGE)
        }
    }
}
