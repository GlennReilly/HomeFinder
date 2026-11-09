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

/** Turns the three boxes of the NAPLAN form into a [NaplanEntry], or says what is wrong before anything is sent (ADR 0068, 0069). */
object NaplanInput {
    const val MESSAGE_SCORE_MISSING: String = "Enter the difference from the national average, for example -41"
    const val MESSAGE_SCORE_RANGE: String = "The difference must be a whole number from ${NaplanRules.MIN_SCORE} to +${NaplanRules.MAX_SCORE}"
    const val MESSAGE_YEAR_MISSING: String = "Enter the year the figure is from, for example 2025"
    const val MESSAGE_YEAR_RANGE: String = "The year must be from ${NaplanRules.FIRST_YEAR} to this year"
    const val MESSAGE_NOTE_LONG: String = "Keep the note to ${NaplanRules.MAX_NOTE_LENGTH} characters"

    /** Longer than any number the form takes, so a long string of digits is refused before it is turned into a number. */
    private val WHOLE_NUMBER = Regex("[0-9]{1,6}")

    /** A whole number of points with an optional sign: "-41", "+25", "0". */
    private val SIGNED_WHOLE_NUMBER = Regex("[+-]?[0-9]{1,6}")

    /** Minus signs as other sites print them (the typographic minus and dashes), read as an ordinary "-". */
    private val MINUS_LOOKALIKES = Regex("[\u2212\u2013\u2014]")
    private const val PLUS = "+"

    fun parse(scoreText: String, yearText: String, noteText: String, currentYear: Int): ParsedNaplan {
        val score = MINUS_LOOKALIKES.replace(scoreText.trim(), "-")
        if (score.isEmpty()) return ParsedNaplan.Invalid(MESSAGE_SCORE_MISSING)
        if (!SIGNED_WHOLE_NUMBER.matches(score)) return ParsedNaplan.Invalid(MESSAGE_SCORE_RANGE)
        val year = yearText.trim()
        if (year.isEmpty()) return ParsedNaplan.Invalid(MESSAGE_YEAR_MISSING)
        if (!WHOLE_NUMBER.matches(year)) return ParsedNaplan.Invalid(MESSAGE_YEAR_RANGE)
        return when (val checked = NaplanRules.check(NaplanEntry(score.removePrefix(PLUS).toInt(), year.toInt(), noteText), currentYear)) {
            is NaplanCheck.Valid -> ParsedNaplan.Valid(checked.entry)
            is NaplanCheck.Invalid -> ParsedNaplan.Invalid(
                when (checked.problem) {
                    NaplanProblem.SCORE_OUT_OF_RANGE -> MESSAGE_SCORE_RANGE
                    NaplanProblem.YEAR_OUT_OF_RANGE -> MESSAGE_YEAR_RANGE
                    NaplanProblem.NOTE_TOO_LONG -> MESSAGE_NOTE_LONG
                },
            )
        }
    }
}
