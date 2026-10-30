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

    @Test
    fun `a loaded card links to the suburb's Homely page in a new tab without copying anything from it`() {
        val out = html(SuburbCardState.Loaded(report))
        assertTrue("""href="https://www.homely.com.au/suburb-profile/hamilton-nsw-2303"""" in out, out)
        assertTrue("""target="_blank"""" in out && """rel="noopener noreferrer"""" in out)
        assertTrue(HOMELY_LINK_TEXT in out)
        assertFalse("homely.com.au" in html(SuburbCardState.Loaded(report.copy(postcode = null))), "no postcode, no link")
        assertFalse(HOMELY_LINK_TEXT in html(SuburbCardState.Loaded(report.copy(postcode = null))))
        assertFalse("homely.com.au" in html(SuburbCardState.Loading("9001", SuburbRef("9001", "HAMILTON", "2303"))))
    }

    private fun withRating(stars: Double, reviews: Int?) = report.copy(
        homelyRating = Attribute(au.homefinder.shared.model.HomelyRating(stars, reviews), Confidence.LOW, au.homefinder.shared.model.HomelyRating.SOURCE,
            kotlinx.datetime.LocalDate(2026, 10, 30), "typed in by you"),
    )

    @Test
    fun `without a rating the card has an empty form and no Homely row or remove button`() {
        val out = html(SuburbCardState.Loaded(report))
        assertTrue("data-rating-form" in out && "Your Homely rating" in out)
        assertTrue("name=\"stars\"" in out && "name=\"reviews\"" in out && ">Save<" in out)
        assertFalse("value=\"" in out.substringAfter("data-rating-form"), "the boxes start empty")
        assertFalse("placeholder" in out, "and show no example text such as 2.1 or 7")
        assertFalse("Homely rating</dt>" in out)
        assertFalse("remove-rating" in out)
        assertTrue("Nothing is fetched from Homely" in out)
    }

    @Test
    fun `under the price change come the Homely link, then the rating, then its form, and the other rows follow`() {
        val out = html(SuburbCardState.Loaded(withRating(2.1, 7)))
        assertTrue("2.1 ★ (7 reviews)" in out)
        val area = out.indexOf("Area</dt>")
        val unit = out.indexOf("Median unit price</dt>")
        val change = out.indexOf("Price change, 12 months</dt>")
        val link = out.indexOf("homely.com.au/suburb-profile")
        val homely = out.indexOf("Homely rating</dt>")
        val form = out.indexOf("data-rating-form")
        val sales = out.indexOf("Sales behind the prices</dt>")
        assertTrue(area in 0 until unit && unit < change && change < link && link < homely && homely < form && form < sales, "order: $area $unit $change $link $homely $form $sales")
        assertTrue("Source: Homely (entered by you) · as of 2026-10-30 · low confidence" in out)
        val formHtml = out.substringAfter("data-rating-form")
        assertTrue("value=\"2.1\"" in formHtml && "value=\"7\"" in formHtml)
        assertTrue("data-action=\"remove-rating\"" in formHtml)
    }

    @Test
    fun `without a rating the link and then the form sit under the price change`() {
        val out = html(SuburbCardState.Loaded(report))
        val change = out.indexOf("Price change, 12 months</dt>")
        val link = out.indexOf("homely.com.au/suburb-profile")
        val form = out.indexOf("data-rating-form")
        val sales = out.indexOf("Sales behind the prices</dt>")
        assertTrue(change in 0 until link && link < form && form < sales, "order: $change $link $form $sales")
        assertFalse("Homely rating</dt>" in out)
    }

    @Test
    fun `every row is still shown exactly once with the form in the middle`() {
        val out = html(SuburbCardState.Loaded(withRating(2.1, 7)))
        au.homefinder.app.selection.SuburbCardRows.of(report).forEach { row ->
            assertEquals(1, Regex("<dt>" + Regex.escape(row.label) + "</dt>").findAll(out).count(), row.label)
        }
        assertEquals(1, Regex("data-rating-form").findAll(out).count())
    }

    @Test
    fun `a score with no review count leaves the reviews box empty`() {
        val form = html(SuburbCardState.Loaded(withRating(3.5, null))).substringAfter("data-rating-form")
        assertTrue("value=\"3.5\"" in form)
        assertEquals(1, Regex("value=\"").findAll(form.substringBefore("</form>")).count())
    }

    @Test
    fun `the form is only on a loaded card`() {
        assertFalse("data-rating-form" in html(SuburbCardState.Loading("9001", SuburbRef("9001", "HAMILTON", "2303"))))
        assertFalse("data-rating-form" in html(SuburbCardState.Failed("9001", "x")))
    }
}
