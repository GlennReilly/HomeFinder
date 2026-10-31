package au.homefinder.app.ui

import au.homefinder.app.selection.CardLayout
import au.homefinder.app.selection.SuburbCardRows
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
    fun `a loaded card names the suburb, shows no area and marks every statistic unknown`() {
        val out = html(SuburbCardState.Loaded(report))
        assertTrue("<h2>HAMILTON</h2>" in out)
        assertTrue("Postcode 2303" in out)
        assertFalse("km²" in out)
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
        val house = out.indexOf("Median house price</dt>")
        val block = out.indexOf("Typical block size</dt>")
        val unit = out.indexOf("Median unit price</dt>")
        val change = out.indexOf("Price change, 12 months</dt>")
        val link = out.indexOf("homely.com.au/suburb-profile")
        val homely = out.indexOf("Homely rating</dt>")
        val form = out.indexOf("data-rating-form")
        val sales = out.indexOf("Sales behind the prices</dt>")
        assertTrue(house in 0 until block && block < unit && unit < change && change < link && link < homely && homely < form && form < sales, "order: $house $block $unit $change $link $homely $form $sales")
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

    private fun withSchools(vararg schools: au.homefinder.shared.model.School) = report.copy(
        schools = Attribute(
            au.homefinder.shared.model.SuburbSchools(schools.toList(), emptyList(), averageIcsea = 1032),
            Confidence.HIGH, "NSW Department of Education", kotlinx.datetime.LocalDate(2026, 10, 2), "ICSEA measures advantage, not quality",
        ),
    )

    private fun school(name: String, icsea: Int?, website: String?, enrolments: Int? = 412, label: String? = "Primary School") =
        au.homefinder.shared.model.School("1", name, au.homefinder.shared.model.SchoolSector.PUBLIC, au.homefinder.shared.model.SchoolLevel.PRIMARY, icsea, enrolments, website, label)

    @Test
    fun `the schools are listed under the Schools row with their ICSEA, size and own website, and a My School link`() {
        val out = html(SuburbCardState.Loaded(withSchools(school("Hamilton Public School", 1051, "https://hamilton-p.schools.nsw.gov.au"), school("Small School", null, null, enrolments = null))))
        assertTrue("1 public school" !in out && "2 public schools, average ICSEA 1,032 (1,000 is average)" in out)
        assertTrue("""<a href="https://hamilton-p.schools.nsw.gov.au" target="_blank" class="external-link" rel="noopener noreferrer">Hamilton Public School</a>""" in out, out)
        assertTrue("Primary School · ICSEA 1,051 · 412 students" in out)
        assertTrue("""<span class="school-name">Small School</span><span class="detail">Primary School · ICSEA not published</span>""" in out, out)
        assertTrue("""href="https://www.myschool.edu.au/"""" in out && MY_SCHOOL_LINK_TEXT in out)
        val row = out.indexOf("Schools</dt>")
        assertTrue(row in 0 until out.indexOf("Hamilton Public School") && out.indexOf("Hamilton Public School") < out.indexOf("Parks</dt>"), "the list sits inside the Schools row")
    }

    @Test
    fun `a school with no usable website is just a name, and a script address is never linked`() {
        val out = html(SuburbCardState.Loaded(withSchools(school("Sneaky School", 1000, "javascript:alert(1)"))))
        assertFalse("javascript:" in out)
        assertTrue("""<span class="school-name">Sneaky School</span>""" in out)
    }

    @Test
    fun `a suburb with no public school shows no list and no My School link`() {
        val out = html(SuburbCardState.Loaded(withSchools()))
        assertTrue("No public schools inside the suburb" in out)
        assertFalse("myschool.edu.au" in out)
        assertFalse("""class="schools"""" in out)
    }

    private fun custom(layout: CardLayout, customising: Boolean) =
        createHTML().div { suburbCard(SuburbCardState.Loaded(report), layout, customising) }

    private fun labelsIn(out: String) = Regex("<dt>([^<]*)</dt>").findAll(out).map { it.groupValues[1] }.toList()

    @Test
    fun `a hidden row is not shown and the order you chose is used`() {
        val layout = CardLayout().toggle("Flood").moveDown("Median house price", SuburbCardRows.of(report))
        val labels = labelsIn(custom(layout, customising = false))
        assertFalse("Flood" in labels)
        assertEquals(listOf("Typical block size", "Median house price", "Median unit price"), labels.take(3))
        assertTrue("Customise" in custom(layout, false))
        assertFalse("row-controls" in custom(layout, false))
    }

    @Test
    fun `customising shows every row with controls, hidden ones dimmed, and Done and Reset`() {
        val out = custom(CardLayout().toggle("Flood"), customising = true)
        assertEquals(12, labelsIn(out).size)
        assertEquals(12, Regex("""class="row-controls"""").findAll(out).count())
        assertEquals(1, Regex("""class="row row-hidden"""").findAll(out).count())
        assertTrue("""aria-label="Show Flood"""" in out)
        assertTrue("""aria-label="Hide Likely brick"""" in out)
        assertTrue(Regex("""aria-label="Move Median house price up"[^>]*disabled""").containsMatchIn(out), "the first row cannot move up")
        assertTrue(Regex("""aria-label="Move Transport down"[^>]*disabled""").containsMatchIn(out), "the last row cannot move down")
        assertTrue("""data-action="customise-done"""" in out && """data-action="layout-reset"""" in out)
        assertFalse("""data-action="customise"""" in out)
    }

    @Test
    fun `the default layout offers no reset`() {
        assertFalse("layout-reset" in custom(CardLayout(), customising = true))
    }

    @Test
    fun `the Homely block stays under the price change row wherever it is moved, hidden or not`() {
        val rows = SuburbCardRows.of(report)
        val moved = CardLayout().moveUp(SuburbCardRows.PRICE_CHANGE_LABEL, rows).moveUp(SuburbCardRows.PRICE_CHANGE_LABEL, rows)
        val out = custom(moved, false)
        // The order is now house price, price change, block size, unit price: the Homely block follows the price change row.
        assertTrue(out.indexOf("Median house price") < out.indexOf("Price change, 12 months"))
        assertTrue(out.indexOf("Price change, 12 months") < out.indexOf("homely.com.au") && out.indexOf("homely.com.au") < out.indexOf("Typical block size"))
        val hidden = custom(CardLayout().toggle(SuburbCardRows.PRICE_CHANGE_LABEL), false)
        assertFalse("Price change, 12 months" in hidden)
        assertTrue(hidden.indexOf("Sales behind") > hidden.indexOf("homely.com.au"), "the block keeps the hidden row's place")
        assertTrue("Your Homely rating" in hidden)
    }

    @Test
    fun `with every row hidden the card says so`() {
        val all = CardLayout(hidden = SuburbCardRows.of(report).map { it.label }.toSet())
        assertTrue(ALL_HIDDEN_HINT in custom(all, false))
    }
}
