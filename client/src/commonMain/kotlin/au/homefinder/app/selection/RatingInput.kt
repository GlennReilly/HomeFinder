package au.homefinder.app.selection

import au.homefinder.shared.model.HomelyRating
import kotlin.math.roundToLong

/** What typing a score into the form came to: a valid rating, or the words to show beside the form. */
sealed interface ParsedRating {
    data class Valid(val rating: HomelyRating) : ParsedRating
    data class Invalid(val message: String) : ParsedRating
}

/** Turns the two text boxes of the rating form into a [HomelyRating], or says what is wrong before anything is sent. */
object RatingInput {
    const val MESSAGE_STARS_MISSING: String = "Enter the stars, for example 2.1"
    const val MESSAGE_STARS_RANGE: String = "Stars must be a number from 0 to 5"
    const val MESSAGE_REVIEWS: String = "Reviews must be a whole number, or leave it empty"
    private const val DECIMAL_STEPS = 10.0
    private val WHOLE_NUMBER = Regex("[0-9]{1,7}")

    fun parse(starsText: String, reviewsText: String): ParsedRating {
        // A comma is accepted as the decimal point ("2,1"), as people type it.
        val stars = starsText.trim().replace(',', '.')
        if (stars.isEmpty()) return ParsedRating.Invalid(MESSAGE_STARS_MISSING)
        val value = stars.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 && it <= HomelyRating.MAX_STARS }
            ?: return ParsedRating.Invalid(MESSAGE_STARS_RANGE)
        val reviewsTrimmed = reviewsText.trim()
        val reviews = when {
            reviewsTrimmed.isEmpty() -> null
            WHOLE_NUMBER.matches(reviewsTrimmed) -> reviewsTrimmed.toInt().takeIf { it <= HomelyRating.MAX_REVIEWS }
                ?: return ParsedRating.Invalid(MESSAGE_REVIEWS)
            else -> return ParsedRating.Invalid(MESSAGE_REVIEWS)
        }
        return ParsedRating.Valid(HomelyRating((value * DECIMAL_STEPS).roundToLong() / DECIMAL_STEPS, reviews))
    }
}
