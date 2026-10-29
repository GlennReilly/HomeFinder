package au.homefinder.app.ui

import au.homefinder.app.selection.SuburbCardState
import au.homefinder.shared.api.SuburbRef
import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.SuburbReport
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuburbCardHtmlTest {
    private fun <T> unknown() = Attribute.unknown<T>("Some source", note = "Not computed yet")
    private val report = SuburbReport(
        "9001", "HAMILTON", "2303", Attribute(4.894, Confidence.HIGH, "NSW Spatial Services", null),
        unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(),
    )

    private fun html(card: SuburbCardState) = createHTML().div { suburbCard(card) }

    @Test
    fun `a loaded card names the suburb, shows the area and marks every statistic unknown`() {
        val out = html(SuburbCardState.Loaded(report))
        assertTrue("<h2>HAMILTON</h2>" in out)
        assertTrue("Postcode 2303" in out)
        assertTrue("4.89 km²" in out)
        assertEquals(12, Regex("""class="value unknown"""").findAll(out).count())
        assertTrue("Source: Some source" in out)
        assertTrue("Not computed yet" in out)
        assertTrue(CARD_DISCLAIMER in out)
    }

    @Test
    fun `loading shows the name from the tap straight away and a progress bar`() {
        val out = html(SuburbCardState.Loading("9001", SuburbRef("9001", "HAMILTON", "2303")))
        assertTrue("<h2>HAMILTON</h2>" in out)
        assertTrue("<progress" in out)
        assertFalse("<dl" in out)
    }

    @Test
    fun `loading from a search has no name yet`() {
        assertTrue("Loading suburb…" in html(SuburbCardState.Loading("9001", null)))
    }

    @Test
    fun `a failed card says why and offers to retry`() {
        val out = html(SuburbCardState.Failed("9001", "the server answered 500"))
        assertTrue("Could not load this suburb: the server answered 500." in out)
        assertTrue("""data-action="retry"""" in out)
    }

    @Test
    fun `the close button is always present and labelled`() {
        val out = html(SuburbCardState.Loaded(report))
        assertTrue("""data-action="close"""" in out)
        assertTrue("""aria-label="Close suburb card"""" in out)
    }

    @Test
    fun `text from the API is escaped`() {
        val out = html(SuburbCardState.Failed("1", "<script>alert(1)</script>"))
        assertFalse("<script>" in out)
    }
}
