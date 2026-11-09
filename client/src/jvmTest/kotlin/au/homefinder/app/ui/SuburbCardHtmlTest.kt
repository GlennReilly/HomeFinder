package au.homefinder.app.ui

import au.homefinder.app.selection.CardGroups
import au.homefinder.app.selection.CardLayout
import au.homefinder.app.selection.SuburbCardRows
import au.homefinder.app.selection.SuburbCardState
import au.homefinder.shared.api.SuburbRef
import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.School
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolSector
import au.homefinder.shared.model.SuburbReport
import au.homefinder.shared.model.SuburbZone
import au.homefinder.shared.model.SuburbZonedSchools
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The HTML with the pretty-printing whitespace between tags removed, so markup can be matched as one string. */
private fun flat(html: String): String = html.replace(Regex(">\\s+<"), "><")

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
        assertTrue("Some source" in out)
        assertTrue("Not computed yet" in out)
        // One closed Details section per row holds the note and then the source (ADR 0072); there is no separate Source section.
        assertTrue(Regex("""<details class="details-fold">\s*<summary>Details</summary>\s*<p class="details-note">\s*Not computed yet</p>\s*<p class="details-source">Source: Some source</p>""").containsMatchIn(out), out)
        assertEquals(Regex("""<details class="details-fold">""").findAll(out).count(), Regex("""<dd class="row-details">""").findAll(out).count())
        assertFalse("source-details" in out || "note-details" in out || "<summary>Source</summary>" in out)
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
        assertTrue("Homely (entered by you) · as of 2026-10-30 · low confidence" in out)
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
        assertEquals(1, Regex("""<details class="rating-edit"""").findAll(out).count())
        assertFalse(Regex("""<details class="details-fold" open""").containsMatchIn(out), "every Details section starts closed")
        assertTrue("""<details class="details-fold"><summary>Details</summary>""" in out, out)
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
        zonedSchools = Attribute(
            SuburbZonedSchools(schools.map { SuburbZone(it, 1.0) }),
            Confidence.MEDIUM, "NSW Department of Education", kotlinx.datetime.LocalDate(2026, 10, 2), "ICSEA measures advantage, not quality",
        ),
    )

    private fun school(name: String, icsea: Int?, website: String?, enrolments: Int? = 412, label: String? = "Primary School") =
        au.homefinder.shared.model.School("1", name, au.homefinder.shared.model.SchoolSector.PUBLIC, au.homefinder.shared.model.SchoolLevel.PRIMARY, icsea, enrolments, website, label)

    @Test
    fun `the schools are listed under the Schools row with their ICSEA, size and own website, and no separate My School line`() {
        val out = html(SuburbCardState.Loaded(withSchools(school("Hamilton Public School", 1051, "https://hamilton-p.schools.nsw.gov.au"), school("Small School", null, null, enrolments = null))))
        assertTrue("Hamilton Public School (Primary School), Small School (Primary School)" in out, "one Schools row names the zoned schools (ADR 0073)")
        assertFalse("Zoned schools</dt>" in out || "public schools" in out, "no second row and no in-suburb listing")
        // One line each (ADR 0071): name and kind, ICSEA, NAPLAN, links; the website link is on the links line, not the name.
        val flat = flat(out)
        assertTrue("""<div class="school-line"><span class="school-name">Hamilton Public School</span><span class="detail"> · Primary School · 100% of the suburb's lots</span></div><div class="school-icsea">ICSEA 1,051</div><div class="naplan-line">""" in flat, flat)
        assertTrue("""<div class="school-links"><a href="https://hamilton-p.schools.nsw.gov.au" target="_blank" class="external-link" rel="noopener noreferrer" title="Hamilton Public School: school website (opens in a new tab)">School website</a>""" in flat, flat)
        assertTrue("""<div class="school-line"><span class="school-name">Small School</span><span class="detail"> · Primary School · 100% of the suburb's lots</span></div><div class="naplan-line">""" in flat, "no ICSEA line without an ICSEA: $flat")
        assertFalse("Look these schools up" in out || "myschool.edu.au" in out, "the My School line is gone; only the NAPLAN form links there")
        val row = out.indexOf("Schools</dt>")
        assertTrue(row in 0 until out.indexOf("Hamilton Public School"), "the list sits inside the Schools row")
    }

    @Test
    fun `a school with no usable website is just a name, and a script address is never linked`() {
        val out = html(SuburbCardState.Loaded(withSchools(school("Sneaky School", 1000, "javascript:alert(1)"))))
        assertFalse("javascript:" in out)
        assertTrue("""<span class="school-name">Sneaky School</span>""" in out)
    }

    @Test
    fun `a suburb with no zone data shows the Schools row as Unknown with the reason, no list and no My School link`() {
        val out = html(SuburbCardState.Loaded(report))
        assertTrue(Regex("""<dt>Schools</dt>""").containsMatchIn(out) && "Not computed yet" in out)
        assertFalse("Zoned schools</dt>" in out)
        assertFalse("myschool.edu.au" in out)
        assertFalse("""class="zones"""" in out)
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
    fun `with a Schools group on the card a Jump to Schools button sits in the pinned block, labelled, and not while customising`() {
        val card = SuburbCardState.Loaded(withSchools(school("Hamilton Public School", 1051, null)))
        val out = html(card, showUnknown = false)
        val pinned = out.substringAfter("""class="$STICKY_TOP_CLASS"""").substringBefore("""<dl class="rows">""")
        assertTrue("""data-action="${CardActions.JUMP_SCHOOLS}"""" in pinned, pinned)
        assertTrue(""">$JUMP_TO_SCHOOLS_TEXT</button>""" in pinned)
        assertTrue("""aria-label="$JUMP_TO_SCHOOLS_LABEL"""" in pinned)
        assertTrue("""class="$JUMP_TO_SCHOOLS_CLASS"""" in pinned, "it is a 44 px layout button")
        assertFalse(CardActions.JUMP_SCHOOLS in createHTML().div { suburbCard(card, customising = true) })
    }

    @Test
    fun `with no Schools group to go to there is no Jump to Schools button`() {
        assertFalse(CardActions.JUMP_SCHOOLS in html(SuburbCardState.Loaded(report), showUnknown = false), "unknown Schools row left off")
        val hidden = CardLayout().toggle(SuburbCardRows.SCHOOLS_LABEL)
        assertFalse(CardActions.JUMP_SCHOOLS in createHTML().div { suburbCard(SuburbCardState.Loaded(withSchools(school("A", 1000, null))), hidden) })
        assertFalse(CardActions.JUMP_SCHOOLS in html(SuburbCardState.Loading("9001", null)))
    }

    @Test
    fun `rows without data are left off, with a note that counts them and offers to show them`() {
        val out = html(SuburbCardState.Loaded(report), showUnknown = false)
        assertEquals(listOf("Houses for sale", "Units for sale"), labelsIn(out), "every row of this report is unknown; only the for-sale links (ADR 0061) are left")
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

    private fun loadedWithPrices(houses: Long?, units: Long?) = report.copy(
        medianHousePriceAud = houses?.let { Attribute(it, Confidence.HIGH, "Sales", null) } ?: unknown(),
        medianUnitPriceAud = units?.let { Attribute(it, Confidence.HIGH, "Sales", null) } ?: unknown(),
    )

    @Test
    fun `under the house and unit prices there are links to the portals that open in a new tab`() {
        val out = html(SuburbCardState.Loaded(loadedWithPrices(900_000, 500_000)), showUnknown = false)
        assertTrue("Houses for sale: " in out && "Units for sale: " in out)
        assertTrue("""href="https://www.domain.com.au/sale/hamilton-nsw-2303/house/"""" in out)
        assertTrue("""href="https://www.realestate.com.au/buy/property-unit+apartment-in-hamilton,+nsw+2303/list-1"""" in out)
        assertEquals(4, Regex("""target="_blank"""").findAll(out.substringBefore("homely")).count() , "two links under each price")
        assertTrue(Regex("""<a [^>]*rel="noopener noreferrer"[^>]*>realestate\.com\.au</a>""").containsMatchIn(out))
        // The links sit inside the price's own row, under its value.
        val house = out.indexOf("Median house price")
        assertTrue(house < out.indexOf("Houses for sale: ") && out.indexOf("Houses for sale: ") < out.indexOf("Median unit price"))
    }

    @Test
    fun `a price with no value is left off but its for-sale links stay`() {
        val out = html(SuburbCardState.Loaded(loadedWithPrices(null, 500_000)), showUnknown = false)
        assertFalse("Median house price" in out, "the unknown price row is left off")
        assertTrue("<dt>Houses for sale</dt>" in out)
        assertTrue("""href="https://www.domain.com.au/sale/hamilton-nsw-2303/house/"""" in out)
        // Revealing the unknown rows puts the links back under the price row itself.
        val shown = html(SuburbCardState.Loaded(loadedWithPrices(null, 500_000)), showUnknown = true)
        assertTrue("Median house price" in shown && "<dt>Houses for sale</dt>" !in shown && "Houses for sale: " in shown)
    }

    @Test
    fun `a suburb without a postcode has no for-sale links`() {
        val out = html(SuburbCardState.Loaded(loadedWithPrices(900_000, 500_000).copy(postcode = null)), showUnknown = false)
        assertFalse("for sale" in out)
    }

    @Test
    fun `customising keeps each link under its row, and a row you hid takes its links with it`() {
        val layout = CardLayout(hidden = setOf(SuburbCardRows.MEDIAN_UNIT_LABEL))
        val out = createHTML().div { suburbCard(SuburbCardState.Loaded(loadedWithPrices(900_000, 500_000)), layout, customising = false, showUnknown = false) }
        assertTrue("Houses for sale: " in out)
        assertFalse("Units for sale" in out)
        val editing = createHTML().div { suburbCard(SuburbCardState.Loaded(loadedWithPrices(900_000, 500_000)), layout, customising = true, showUnknown = false) }
        assertTrue("Units for sale: " in editing && "Houses for sale: " in editing)
    }

    @Test
    fun `the Schools row lists each zone under it, with a link to the school's website when it has one`() {
        val zoned = SuburbZonedSchools(
            listOf(
                SuburbZone(School("10", "Hamilton Public School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, icsea = 1041, website = "https://hamilton-p.schools.nsw.gov.au", levelLabel = "primary, K-6"), 0.82),
                SuburbZone(School("20", "Newcastle High School", SchoolSector.PUBLIC, SchoolLevel.HIGH, icsea = 1017, levelLabel = "high, 7-12"), 0.76),
            ),
            2026,
        )
        val out = html(SuburbCardState.Loaded(report.copy(zonedSchools = Attribute(zoned, Confidence.MEDIUM, "NSW DoE", null, "A note"))))
        assertTrue("<dt>Schools</dt>" in out && "Zoned schools</dt>" !in out && "Hamilton Public School (primary), Newcastle High School (high)" in out, out)
        assertTrue("""href="https://hamilton-p.schools.nsw.gov.au"""" in out && " · primary, K-6 · 82% of the suburb's lots</span></div><div class=\"school-icsea\">ICSEA 1,041</div>" in flat(out), out)
        assertTrue("Newcastle High School</span>" in out && ">ICSEA 1,017<" in out, out)
    }

    private val headings get() = { out: String -> Regex("""<summary>([^<]*)</summary>""").findAll(out).map { it.groupValues[1] }.filter { it !in setOf(DetailsFold.SUMMARY_TEXT, RATING_EDIT_TEXT) }.toList() }

    @Test
    fun `rows sit in folding groups, and only the first starts open`() {
        val out = custom(CardLayout(), false)
        assertEquals(listOf("Prices and blocks", "People and area", "Crime", "Location", "Schools"), headings(out))
        assertTrue(Regex("""<details class="card-group" data-group="Prices and blocks" open="open">""").containsMatchIn(out), out)
        assertEquals(1, Regex("""class="card-group"[^>]* open="open"""").findAll(out).count(), "every other group starts closed")
    }

    @Test
    fun `a group you opened stays open, and one you closed stays closed`() {
        val out = createHTML().div { suburbCard(SuburbCardState.Loaded(report), CardLayout(), false, true, mapOf("Crime" to true, "Prices and blocks" to false)) }
        assertTrue(Regex("""data-group="Crime" open="open"""").containsMatchIn(out))
        assertFalse(Regex("""data-group="Prices and blocks" open="open"""").containsMatchIn(out))
    }

    @Test
    fun `groups follow the order you chose, and customising opens them all with group controls`() {
        val moved = CardLayout().moveGroup("Crime", SuburbCardRows.of(report), CardGroups.SUBURB, -1)
        assertEquals(listOf("Prices and blocks", "Crime", "People and area", "Location", "Schools"), headings(custom(moved, false)))
        val out = custom(CardLayout(), true)
        assertEquals(5, Regex("""class="card-group"[^>]* open="open"""").findAll(out).count())
        assertTrue(Regex("""aria-label="Move the Prices and blocks group up"[^>]*disabled""").containsMatchIn(out), "the first group cannot move up")
        assertTrue("""aria-label="Move the Crime group down"""" in out)
        assertTrue(Regex("""aria-label="Move Violent crime up"[^>]*disabled""").containsMatchIn(out), "a row cannot leave its group")
    }

    @Test
    fun `a group whose rows are all hidden is left off`() {
        val hidden = custom(CardLayout(hidden = setOf("Violent crime", "Break-ins, car and home theft")), false)
        assertFalse("Crime" in headings(hidden))
    }
}
