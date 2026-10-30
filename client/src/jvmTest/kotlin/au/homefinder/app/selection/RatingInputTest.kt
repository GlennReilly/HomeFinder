package au.homefinder.app.selection

import au.homefinder.shared.model.HomelyRating
import kotlin.test.Test
import kotlin.test.assertEquals

class RatingInputTest {
    private fun valid(stars: String, reviews: String = "") = (RatingInput.parse(stars, reviews) as ParsedRating.Valid).rating
    private fun invalid(stars: String, reviews: String = "") = (RatingInput.parse(stars, reviews) as ParsedRating.Invalid).message

    @Test
    fun `stars and reviews become a rating, and reviews are optional`() {
        assertEquals(HomelyRating(2.1, 7), valid("2.1", "7"))
        assertEquals(HomelyRating(2.1, null), valid("2.1"))
        assertEquals(HomelyRating(0.0, 0), valid("0", "0"))
        assertEquals(HomelyRating(5.0, null), valid(" 5 ", "  "))
    }

    @Test
    fun `a comma works as the decimal point and more places are rounded to one`() {
        assertEquals(2.1, valid("2,1").stars)
        assertEquals(2.1, valid("2.14").stars)
        assertEquals(3.0, valid("2.96").stars)
        assertEquals(5.0, valid("4.96").stars)
    }

    @Test
    fun `missing, out of range or non-numeric stars are explained`() {
        assertEquals(RatingInput.MESSAGE_STARS_MISSING, invalid(""))
        assertEquals(RatingInput.MESSAGE_STARS_MISSING, invalid("   "))
        listOf("5.1", "-1", "abc", "2..1", "NaN", "Infinity", "1e999", "5.04x").forEach { assertEquals(RatingInput.MESSAGE_STARS_RANGE, invalid(it), it) }
    }

    @Test
    fun `reviews must be a whole number within the limit`() {
        listOf("-1", "2.5", "seven", "1e3", "1000001", "99999999").forEach { assertEquals(RatingInput.MESSAGE_REVIEWS, invalid("3", it), it) }
        assertEquals(1_000_000, valid("3", "1000000").reviewCount)
    }
}
