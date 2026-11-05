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

    /** These fixtures are all unknown, so by default the rows are shown (the tests are about layout); the hiding has its own tests below. */
    private fun html(card: SuburbCardState, showUnknown: Boolean = true) = createHTML().div { suburbCard(card, showUnknown = showUnknown) }

    @Test
    fun `a loaded card names the suburb, shows no area and marks every statistic unknown`() {
        val out = html(SuburbCardState.Loaded(report))
        assertTrue("<h2>HAMILTON</h2>" in out)
        assertTrue("Postcode 2303" in out)
        assertFalse("km²" in out)
        assertEquals(17, Regex("""class="value unknown"""").findAll(out).count())
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
        assertTrue(Regex("""class="close"[^>]*>×</button>""").containsMatchIn(out), "the button shows a cross, not the word")
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
    fun `the other rows come first, then the Homely rating, the link to its reviews and its form at the bottom, then the disclaimer`() {
        val out = html(SuburbCardState.Loaded(withRating(2.1, 7)))
        assertTrue("2.1 ★ (7 reviews)" in out)
        val house = out.indexOf("Median house price</dt>")
        val block = out.indexOf("Typical block size</dt>")
        val unit = out.indexOf("Median unit price</dt>")
        val change = out.indexOf("Price change, 12 months</dt>")
        val link = out.indexOf("homely.com.au/suburb-profile")
        val homely = out.indexOf("Homely rating</dt>")
        val form = out.indexOf("data-rating-form")
        val social = out.indexOf("Socio-economic advantage</dt>")
        val disclaimer = out.indexOf(CARD_DISCLAIMER)
        assertTrue(house in 0 until block && block < unit && unit < change && change < social, "rows keep their order: $house $block $unit $change $social")
        assertTrue(social < homely && homely < link && link < form && form < disclaimer, "Homely block last: $social $homely $link $form $disclaimer")
        assertTrue("Source: Homely (entered by you) · as of 2026-10-30 · low confidence" in out)
        val formHtml = out.substringAfter("data-rating-form")
        assertTrue("value=\"2.1\"" in formHtml && "value=\"7\"" in formHtml)
        assertTrue("data-action=\"remove-rating\"" in formHtml)
    }

    @Test
    fun `the rating form is hidden behind an edit link and the link, rating and form share one Homely block`() {
        val out = html(SuburbCardState.Loaded(withRating(2.1, 7)))
        assertTrue("""<details class="rating-edit"><summary>$RATING_EDIT_TEXT</summary>""" in out, out)
        assertFalse("<details class=\"rating-edit\" open" in out, "the form starts closed")
        val block = out.substringAfter("""<section class="homely">""").substringBefore("</section>")
        assertTrue("Homely rating</dt>" in block && HOMELY_LINK_TEXT in block && "data-rating-form" in block, "one block holds the rating, the link and the form")
        assertFalse(HOMELY_LINK_TEXT in out.substringBefore("""<section class="homely">"""), "the link is no longer above the block")
        assertEquals(1, Regex("""<details""").findAll(out).count())
        assertTrue(RATING_EDIT_TEXT in html(SuburbCardState.Loaded(report)), "with no rating the edit link is still there")
    }

    @Test
    fun `without a rating the link and then the form sit at the bottom, above the disclaimer`() {
        val out = html(SuburbCardState.Loaded(report))
        val change = out.indexOf("Price change, 12 months</dt>")
        val link = out.indexOf("homely.com.au/suburb-profile")
        val form = out.indexOf("data-rating-form")
        val social = out.indexOf("Socio-economic advantage</dt>")
        val disclaimer = out.indexOf(CARD_DISCLAIMER)
        assertTrue(change in 0 until social && social < link && link < form && form < disclaimer, "order: $change $social $link $form $disclaimer")
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

    private fun custom(layout: CardLayout, customising: Boolean, showUnknown: Boolean = true) =
        createHTML().div { suburbCard(SuburbCardState.Loaded(report), layout, customising, showUnknown) }

    private fun labelsIn(out: String) = Regex("<dt>([^<]*)</dt>").findAll(out).map { it.groupValues[1] }.toList()

    @Test
    fun `a hidden row is not shown and the order you chose is used`() {
        val layout = CardLayout().toggle("Parks").moveDown("Median house price", SuburbCardRows.of(report))
        val labels = labelsIn(custom(layout, customising = false))
        assertFalse("Parks" in labels)
        assertEquals(listOf("Typical block size", "Median house price", "Median unit price"), labels.take(3))
        assertTrue("Customise" in custom(layout, false))
        assertFalse("row-controls" in custom(layout, false))
    }

    @Test
    fun `customising shows every row with controls, hidden ones dimmed, and Done and Reset`() {
        val out = custom(CardLayout().toggle("Parks"), customising = true)
        assertEquals(17, labelsIn(out).size)
        assertEquals(17, Regex("""class="row-controls"""").findAll(out).count())
        assertEquals(1, Regex("""class="row row-hidden"""").findAll(out).count())
        assertTrue("""aria-label="Show Parks"""" in out)
        assertTrue("""aria-label="Hide Shopping"""" in out)
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
    fun `the Homely block stays at the bottom wherever rows are moved or hidden, and is not a movable row`() {
        val rows = SuburbCardRows.of(report)
        val moved = CardLayout().moveUp(SuburbCardRows.PRICE_CHANGE_LABEL, rows).moveUp(SuburbCardRows.PRICE_CHANGE_LABEL, rows)
        val out = custom(moved, false)
        assertTrue(out.indexOf("Median house price") < out.indexOf("Price change, 12 months"))
        assertTrue(out.indexOf("Typical block size") < out.indexOf("homely.com.au"), "the block follows every row")
        val hidden = custom(CardLayout().toggle(SuburbCardRows.PRICE_CHANGE_LABEL), false)
        assertFalse("Price change, 12 months" in hidden)
        assertTrue(hidden.indexOf("Socio-economic advantage") < hidden.indexOf("homely.com.au"))
        assertTrue("Your Homely rating" in hidden)
        val customising = custom(CardLayout(), true)
        assertTrue(customising.indexOf("Transport") < customising.indexOf("homely.com.au"), "customising lists every row above the block")
        assertFalse("Move Homely" in customising || "Hide Homely" in customising, "the block has no row controls")
    }

    @Test
    fun `with every row hidden the card says so`() {
        val all = CardLayout(hidden = SuburbCardRows.of(report).map { it.label }.toSet())
        assertTrue(ALL_HIDDEN_HINT in custom(all, false))
    }

    @Test
    fun `the header and the Customise bar sit together in the pinned block, ahead of the rows`() {
        val out = html(SuburbCardState.Loaded(report))
        val pinned = out.substringAfter("""class="$STICKY_TOP_CLASS"""").substringBefore("""<dl class="rows">""")
        assertTrue("<h2>HAMILTON</h2>" in pinned && "Postcode 2303" in pinned && "Close suburb card" in pinned, pinned)
        assertTrue("""data-action="customise"""" in pinned, "the Customise button is pinned too")
        // While loading there is no Customise bar yet, but the name and the close button are still pinned.
        val loading = html(SuburbCardState.Loading("9001", SuburbRef("9001", "HAMILTON", "2303")))
        assertTrue(STICKY_TOP_CLASS in loading && "customise" !in loading)
    }

    @Test
    fun `rows without data are left off, with a note that counts them and offers to show them`() {
        val out = html(SuburbCardState.Loaded(report), showUnknown = false)
        assertEquals(0, labelsIn(out).size, "every row of this report is unknown")
        assertEquals(0, Regex("""class="value unknown"""").findAll(out).count())
        assertTrue("17 rows without data are hidden." in out, out)
        assertTrue("""data-action="show-unknown"""" in out && ">Show them<" in out)
        assertTrue(CARD_DISCLAIMER in out && "<h2>HAMILTON</h2>" in out)
        assertFalse(ALL_HIDDEN_HINT in out, "rows are left off because they are unknown, not because you hid them all")
    }

    @Test
    fun `showing the unknown rows draws them with their reasons and offers to hide them again`() {
        val out = html(SuburbCardState.Loaded(report), showUnknown = true)
        assertEquals(17, labelsIn(out).size)
        assertTrue("Showing 17 rows without data." in out && """data-action="hide-unknown"""" in out && ">Hide them again<" in out)
    }

    @Test
    fun `customising lists every row and no note, and rows you hid yourself are not counted`() {
        val customising = custom(CardLayout(), customising = true, showUnknown = false)
        assertEquals(17, labelsIn(customising).size)
        assertFalse("without data" in customising)
        val oneHidden = createHTML().div { suburbCard(SuburbCardState.Loaded(report), CardLayout(hidden = setOf("Parks")), customising = false, showUnknown = false) }
        assertTrue("16 rows without data are hidden." in oneHidden, oneHidden)
    }

    @Test
    fun `the Homely block comes after the note about hidden rows and before the disclaimer`() {
        val out = html(SuburbCardState.Loaded(report), showUnknown = false)
        assertTrue(out.indexOf("unknown-note") in 0 until out.indexOf("""class="homely""""), out)
        assertTrue(out.indexOf("""class="homely"""") < out.indexOf(CARD_DISCLAIMER), "the disclaimer stays last")
        val revealed = html(SuburbCardState.Loaded(report), showUnknown = true)
        assertTrue(revealed.indexOf("Transport") in 0 until revealed.indexOf("""class="homely""""), "revealed rows are above the block too")
    }
}
