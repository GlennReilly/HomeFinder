package au.homefinder.app.selection

import au.homefinder.shared.model.NaplanCheck
import au.homefinder.shared.model.NaplanEntry
import au.homefinder.shared.model.NaplanProblem
import au.homefinder.shared.model.NaplanRules

/** What typing into the NAPLAN form came to: a valid entry, or the words to show beside the form. */
sealed interface ParsedNaplan {
    data class Valid(val entry: NaplanEntry) : ParsedNaplan
    data class Invalid(val message: String) : ParsedNaplan
}

/** Turns the two boxes of the NAPLAN form into a [NaplanEntry], or says what is wrong before anything is sent (ADR 0068, 0069). */
object NaplanInput {
    const val MESSAGE_SCORE_MISSING: String = "Enter the difference from the national average, for example -41"
    const val MESSAGE_SCORE_RANGE: String = "The difference must be a whole number from ${NaplanRules.MIN_SCORE} to +${NaplanRules.MAX_SCORE}"
    const val MESSAGE_YEAR_RANGE: String = "The year must be from ${NaplanRules.FIRST_YEAR} to this year"

    /** Longer than any number the form takes, so a long string of digits is refused before it is turned into a number. */
    private val WHOLE_NUMBER = Regex("[0-9]{1,6}")

    /** A whole number of points with an optional sign: "-41", "+25", "0". */
    private val SIGNED_WHOLE_NUMBER = Regex("[+-]?[0-9]{1,6}")

    /** Minus signs as other sites print them (the typographic minus and dashes), read as an ordinary "-". */
    private val MINUS_LOOKALIKES = Regex("[\u2212\u2013\u2014]")
    private const val PLUS = "+"

    fun parse(scoreText: String, yearText: String, currentYear: Int): ParsedNaplan {
        val score = MINUS_LOOKALIKES.replace(scoreText.trim(), "-")
        if (score.isEmpty()) return ParsedNaplan.Invalid(MESSAGE_SCORE_MISSING)
        if (!SIGNED_WHOLE_NUMBER.matches(score)) return ParsedNaplan.Invalid(MESSAGE_SCORE_RANGE)
        // The year is optional (ADR 0071): an empty box means no year, anything else must be a year in range.
        val yearTrimmed = yearText.trim()
        val year = when {
            yearTrimmed.isEmpty() -> null
            WHOLE_NUMBER.matches(yearTrimmed) -> yearTrimmed.toInt()
            else -> return ParsedNaplan.Invalid(MESSAGE_YEAR_RANGE)
        }
        return when (val checked = NaplanRules.check(NaplanEntry(score.removePrefix(PLUS).toInt(), year), currentYear)) {
            is NaplanCheck.Valid -> ParsedNaplan.Valid(checked.entry)
            is NaplanCheck.Invalid -> ParsedNaplan.Invalid(
                when (checked.problem) {
                    NaplanProblem.SCORE_OUT_OF_RANGE -> MESSAGE_SCORE_RANGE
                    NaplanProblem.YEAR_OUT_OF_RANGE -> MESSAGE_YEAR_RANGE
                },
            )
        }
    }
}
