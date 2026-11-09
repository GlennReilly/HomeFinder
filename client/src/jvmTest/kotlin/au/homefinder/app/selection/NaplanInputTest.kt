package au.homefinder.app.selection

import au.homefinder.shared.model.NaplanEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class NaplanInputTest {
    private fun parse(score: String) = NaplanInput.parse(score)

    @Test
    fun `a signed whole difference is accepted, with spaces trimmed`() {
        assertEquals(ParsedNaplan.Valid(NaplanEntry(-41)), parse(" -41 "))
        assertEquals(ParsedNaplan.Valid(NaplanEntry(25)), parse("+25"))
        assertEquals(ParsedNaplan.Valid(NaplanEntry(-41)), parse("−41"), "a typographic minus as other sites print it")
        assertEquals(ParsedNaplan.Valid(NaplanEntry(-1000)), parse("-1000"))
        assertEquals(ParsedNaplan.Valid(NaplanEntry(0)), parse("0"))
        assertEquals(ParsedNaplan.Valid(NaplanEntry(1000)), parse("1000"))
    }

    @Test
    fun `a missing score says what to type`() {
        assertEquals(ParsedNaplan.Invalid(NaplanInput.MESSAGE_SCORE_MISSING), parse("  "))
    }

    @Test
    fun `a difference that is not a whole number from -1000 to +1000 is refused`() {
        listOf("1001", "-1001", "-41.5", "abc", "5 12", "--41", "+-4", "-", "99999999999").forEach {
            assertEquals(ParsedNaplan.Invalid(NaplanInput.MESSAGE_SCORE_RANGE), parse(it), it)
        }
    }
}
