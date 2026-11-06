package au.homefinder.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Route
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import java.util.regex.Pattern
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Smoke tests of the built web app in a real Chrome (ADR 0032): the page loads and draws a map, a tap selects a suburb,
 * search finds and opens things, and a Homely score can be saved and removed. The backend is [FakeApp]; the one outside
 * service the page uses, the OpenFreeMap basemap, is replaced by a plain background so the tests never depend on the internet.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmokeTest {
    private lateinit var app: FakeApp
    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var context: BrowserContext
    private lateinit var page: Page
    private val pageErrors = mutableListOf<String>()

    @BeforeAll
    fun start() {
        val webRoot = File(requireNotNull(System.getProperty("homefinder.web.root")) { "run through :e2e:e2eTest" })
        check(File(webRoot, "index.html").isFile) { "the web app is not built at $webRoot" }
        app = FakeApp(webRoot)
        playwright = Playwright.create()
        // The Chrome that is installed (also on GitHub's runners); override with E2E_BROWSER_CHANNEL (for example "msedge").
        val channel = System.getenv("E2E_BROWSER_CHANNEL") ?: "chrome"
        browser = playwright.chromium().launch(BrowserType.LaunchOptions().setChannel(channel).setHeadless(true))
    }

    @AfterAll
    fun stop() {
        browser.close()
        playwright.close()
        app.close()
    }

    @BeforeEach
    fun newPage() {
        app.rating = null
        app.failSavingRatings = false
        pageErrors.clear()
        context = browser.newContext(Browser.NewContextOptions().setViewportSize(1100, 750))
        // The basemap style comes from a public service; stand in a plain background with no sources.
        context.route("https://tiles.openfreemap.org/**") { route: Route ->
            route.fulfill(
                Route.FulfillOptions().setStatus(200).setContentType("application/json").setBody(
                    """{"version":8,"name":"smoke","glyphs":"${app.origin}/fonts/{fontstack}/{range}.pbf","sources":{},""" +
                        """"layers":[{"id":"background","type":"background","paint":{"background-color":"#e8e4dc"}}]}""",
                ),
            )
        }
        page = context.newPage()
        page.onPageError { pageErrors += it }
    }

    @AfterEach
    fun closePage() {
        context.close()
        // A page error is an uncaught exception in the app: never expected.
        assertEquals(emptyList(), pageErrors, "uncaught errors in the page")
    }

    private fun openWithKey() {
        context.addInitScript("window.localStorage.setItem('homefinder.apiKey', '${app.apiKey}')")
        page.navigate(app.origin + "/")
        assertThat(page.locator("#map canvas")).isVisible()
    }

    /** The fake suburb has rows with no data, which the card leaves off; this reveals them (ADR 0058). */
    private fun showUnknownRows() {
        page.locator("#card button[data-action=show-unknown]").click()
        assertThat(page.locator("#card button[data-action=hide-unknown]")).isVisible()
    }

    private fun tapSuburb() {
        page.locator("#map canvas").click()
        assertThat(page.locator("#card h2")).hasText("HAMILTON")
    }

    @Test
    fun `the page asks for the key once, then draws the map and remembers the key`() {
        page.navigate(app.origin + "/")
        assertThat(page.locator("#key-prompt")).isVisible()
        page.locator("#key-input").fill(app.apiKey)
        page.locator("#key-form button[type=submit]").click()
        assertThat(page.locator("#key-prompt")).isHidden()
        assertThat(page.locator("#map canvas")).isVisible()
        page.reload()
        assertThat(page.locator("#map canvas")).isVisible()
        assertThat(page.locator("#key-prompt")).isHidden()
    }

    @Test
    fun `selecting a suburb changes only that suburb and nothing else is dimmed`() {
        openWithKey()
        assertThat(page.locator("#map canvas")).isVisible()
        tapSuburb()
        // The map is on the window for the tests; the selected fill and outline name the tapped suburb, and there is no layer that dims the rest.
        val layers = page.evaluate("window.homefinderMap.getStyle().layers.map(l => l.id)").toString()
        assertTrue("suburb-selected-fill" in layers && "suburb-selected-outline" in layers, layers)
        assertFalse("suburb-dim" in layers, "no layer dims the other suburbs: $layers")
        val fill = page.evaluate("JSON.stringify(window.homefinderMap.getFilter('suburb-selected-fill'))").toString()
        val outline = page.evaluate("JSON.stringify(window.homefinderMap.getFilter('suburb-selected-outline'))").toString()
        assertEquals("""["==",["get","suburb_id"],"9001"]""", fill)
        assertEquals(fill, outline)
        page.keyboard().press("Escape")
        assertThat(page.locator("#card")).isHidden()
        assertEquals("""["==",["get","suburb_id"],""]""", page.evaluate("JSON.stringify(window.homefinderMap.getFilter('suburb-selected-fill'))").toString())
    }

    @Test
    fun `tapping the map selects a suburb and shows its card, and Escape closes it`() {
        openWithKey()
        tapSuburb()
        assertThat(page.locator("#card")).containsText("Postcode 2303")
        assertThat(page.locator("#card dl.rows .row").first()).containsText("Median house price")
        // Rows sit in folding groups (ADR 0064): the prices and blocks group starts open and the others start closed.
        assertThat(page.locator("#card details.card-group[data-group='Prices and blocks'] .row")).containsText(
            arrayOf("Median house price", "Typical block size", "Median unit price", "Price change"),
        )
        assertThat(page.locator("#card details.card-group[data-group='Prices and blocks']")).hasAttribute("open", "open")
        assertThat(page.locator("#card details.card-group[data-group='Schools']")).not().hasAttribute("open", "open")
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Area"))).hasCount(0)
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Price change"))).containsText("+4.2% (120 sales)")
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Sales behind the prices"))).hasCount(0)
        assertThat(page.locator("#card a.external-link[href*='homely.com.au']")).hasAttribute("href", "https://www.homely.com.au/suburb-profile/hamilton-nsw-2303")
        // Under the house and unit prices: links to houses and units for sale in the suburb on both portals, each in a new tab (ADR 0061).
        val house = page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Median house price"))
        val unit = page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Median unit price"))
        assertThat(house.locator("a[href='https://www.realestate.com.au/buy/property-house-in-hamilton,+nsw+2303/list-1']")).hasAttribute("target", "_blank")
        assertThat(house.locator("a[href='https://www.domain.com.au/sale/hamilton-nsw-2303/house/']")).hasAttribute("target", "_blank")
        assertThat(unit.locator("a[href='https://www.realestate.com.au/buy/property-unit+apartment-in-hamilton,+nsw+2303/list-1']")).hasAttribute("rel", "noopener noreferrer")
        assertThat(unit.locator("a[href='https://www.domain.com.au/sale/hamilton-nsw-2303/apartment-unit-flat/']")).hasAttribute("target", "_blank")
        page.keyboard().press("Escape")
        assertThat(page.locator("#card")).isHidden()
    }

    @Test
    fun `rows can be hidden and moved on the suburb card, and the choice is remembered`() {
        openWithKey()
        tapSuburb()
        val rows = page.locator("#card dl.rows .row")
        page.locator("#card button[data-action=customise]").click()
        page.locator("#card button[aria-label='Hide Parks']").click()
        page.locator("#card button[aria-label='Move Typical block size up']").click()
        assertThat(page.locator("#card dl.rows .row").first()).containsText("Typical block size")
        assertThat(page.locator("#card .row-hidden")).containsText("Parks")
        page.locator("#card button[data-action=customise-done]").click()
        assertThat(page.locator("#card .row-controls")).hasCount(0)
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Shopping"))).hasCount(1)
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Parks"))).hasCount(0)
        assertThat(rows.first()).containsText("Typical block size")
        page.reload()
        assertThat(page.locator("#map canvas")).isVisible()
        tapSuburb()
        assertThat(rows.first()).containsText("Typical block size")
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Parks"))).hasCount(0)
        page.locator("#card button[data-action=customise]").click()
        page.locator("#card button[data-action=layout-reset]").click()
        assertThat(page.locator("#card dl.rows .row").first()).containsText("Typical block size".let { "Median house price" })
        assertThat(page.locator("#card .row-hidden")).hasCount(0)
    }

    @Test
    fun `the Schools row lists the public schools with their ICSEA and own website, and links to My School`() {
        openWithKey()
        tapSuburb()
        val row = page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Schools"))
        assertThat(row.first()).containsText("1 public school, average ICSEA 1,051 (1,000 is average)")
        assertThat(page.locator("#card ul.schools a")).hasAttribute("href", "https://hamilton-p.schools.nsw.gov.au")
        assertThat(page.locator("#card ul.schools")).containsText("ICSEA 1,051 · 412 students")
        page.locator("#card details.card-group[data-group='Schools'] > summary").click()
        assertThat(page.locator("#card a[href='https://www.myschool.edu.au/']")).isVisible()
    }

    @Test
    fun `the Zoned schools row names the zones and lists each with its share of the suburb and ICSEA`() {
        openWithKey()
        tapSuburb()
        val row = page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Zoned schools"))
        assertThat(row.first()).containsText("Hamilton South Public School (primary), Newcastle High School (high)")
        assertThat(page.locator("#card ul.zones")).containsText("primary, K-6 · 82% of the suburb's lots · ICSEA 1,041")
        assertThat(page.locator("#card ul.zones")).containsText("high, 7-12 · 76% of the suburb's lots · ICSEA 1,017")
        assertThat(row.first()).containsText("Catchments change")
    }

    @Test
    fun `a wrong key shows a failed card instead of crashing`() {
        context.addInitScript("window.localStorage.setItem('homefinder.apiKey', 'not-the-key-0123456789')")
        page.navigate(app.origin + "/")
        assertThat(page.locator("#map canvas")).isVisible()
        page.locator("#map canvas").click()
        assertThat(page.locator("#notice")).containsText("API key was rejected")
    }

    @Test
    fun `search lists suggestions and opens a suburb with Enter`() {
        openWithKey()
        page.locator("#search-input").fill("ham")
        assertThat(page.locator("#search-results [role=option]")).hasCount(1)
        assertThat(page.locator("#search-results [role=option]")).containsText("HAMILTON")
        page.locator("#search-input").press("Enter")
        assertThat(page.locator("#card h2")).hasText("HAMILTON")
    }

    @Test
    fun `search opens an address's lot card, and says so when an address has no lot`() {
        openWithKey()
        page.locator("#search-input").fill("12 main st")
        assertThat(page.locator("#search-results [role=option]")).hasCount(1)
        page.locator("#search-results [role=option]").click()
        assertThat(page.locator("#card h2")).hasText("12 MAIN STREET, HAMILTON NSW 2303")
        // While a lot is shown the first tab is called Address (not Suburb).
        assertThat(page.locator("#tab-suburb")).hasText("Address")
        assertThat(page.locator("#card")).containsText("Block size")
        assertThat(page.locator("#card")).containsText("Moderate, 12.0% on average (steepest 21.3%)")
        assertThat(page.locator("#card")).containsText("35.0 m")
        // Under the block size: the address's own page on each portal, in a new tab (ADR 0061).
        val block = page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Block size"))
        assertThat(block.locator("a[href='https://www.domain.com.au/property-profile/12-main-street-hamilton-nsw-2303']")).hasAttribute("target", "_blank")
        assertThat(block.locator("a[href='https://www.realestate.com.au/property/12-main-st-hamilton-nsw-2303/']")).hasAttribute("target", "_blank")

        page.locator("#search-input").fill("120 main st")
        page.locator("#search-results [role=option]").click()
        assertThat(page.locator("#notice")).hasText("No lot found for this address")
        assertThat(page.locator("#card")).isHidden()
        assertThat(page.locator("#tab-suburb")).hasText("Suburb")
    }

    @Test
    fun `the filter panel counts matching suburbs as the ICSEA slider moves`() {
        openWithKey()
        assertThat(page.locator("#tab-filters")).hasAttribute("aria-selected", "true")
        assertThat(page.locator("#filter-summary")).containsText("Switch on a filter")
        assertThat(page.locator("#filter-icsea-unknown")).containsText("1 of 3 suburbs have no ICSEA")
        assertThat(page.locator("#filters")).containsText("not a measure of school quality")
        page.locator("#filter-icsea-on").check()
        assertThat(page.locator("#filter-summary")).containsText("2 of 3 suburbs match")
        // One slider, "at least": the limit starts at the lowest ICSEA, so switching on changed nothing.
        assertThat(page.locator("#filter-icsea-limit-value")).hasText("960")
        assertThat(page.locator("#filter-icsea-min")).hasCount(0)
        page.locator("#filter-icsea-limit").fill("1000")
        assertThat(page.locator("#filter-icsea-limit-value")).hasText("1000")
        assertThat(page.locator("#filter-summary")).containsText("1 of 3 suburbs match")
        page.locator("#filter-icsea-on").uncheck()
        assertThat(page.locator("#filter-summary")).containsText("Switch on a filter")
    }

    @Test
    fun `filters on the share rented and the beach distance combine, and the sliders show their own units`() {
        openWithKey()
        assertThat(page.locator("#filters")).isVisible()
        // "At most" sliders start at the highest value, "at least" at the lowest, so nothing is excluded until one is moved.
        assertThat(page.locator("#filter-rented-limit-value")).hasText("45%")
        assertThat(page.locator("#filter-beach-limit-value")).hasText("12 km")
        assertThat(page.locator("#filter-rented-unknown")).containsText("1 of 3 suburbs have no Census figure")
        assertThat(page.locator("#filters")).containsText("Homes rented: Test Census · as of 2021-08-10")
        assertThat(page.locator("#filter-seifa-limit-value")).hasText("3")
        assertThat(page.locator("#filters")).containsText("Socio-economic advantage: Test SEIFA")
        assertThat(page.locator("#filter-zoned-limit-value")).hasText("990")
        assertThat(page.locator("#filters")).containsText("Zoned schools' ICSEA: Test catchments")
        assertThat(page.locator("#filter-violent-limit-value")).hasText("31 per 1,000")
        assertThat(page.locator("#filter-violent-unknown")).containsText("1 of 3 suburbs have no violent crime rate")
        assertThat(page.locator("#filters")).containsText("Violent crime: Test BOCSAR")
        page.locator("#filter-rented-on").check()
        assertThat(page.locator("#filter-summary")).containsText("2 of 3 suburbs match")
        page.locator("#filter-rented-limit").fill("30")
        assertThat(page.locator("#filter-rented-limit-value")).hasText("30%")
        assertThat(page.locator("#filter-summary")).containsText("1 of 3 suburbs match")
        // A second filter must also pass: the one suburb rented at most 30% is 12 km from the beach, so a 1 km limit removes it.
        page.locator("#filter-beach-on").check()
        assertThat(page.locator("#filter-summary")).containsText("1 of 3 suburbs match all 2 filters")
        page.locator("#filter-beach-limit").fill("1")
        assertThat(page.locator("#filter-summary")).containsText("0 of 3 suburbs match all 2 filters")
        page.locator("#filter-rented-on").uncheck()
        page.locator("#filter-beach-on").uncheck()
        assertThat(page.locator("#filter-summary")).containsText("Switch on a filter")

        // Moving a slider on its own switches its filter on.
        page.locator("#filter-beach-limit").fill("12")
        assertThat(page.locator("#filter-beach-on")).isChecked()
        assertThat(page.locator("#filter-summary")).containsText("of 3 suburbs match")
        page.locator("#filter-beach-on").uncheck()
    }

    @Test
    fun `search says so when nothing matches`() {
        openWithKey()
        page.locator("#search-input").fill("zzz")
        assertThat(page.locator("#search-results")).containsText("No matches")
    }

    /** The rating form stays closed until the "Edit the Homely rating" link is pressed. */
    private fun openRatingForm() {
        page.locator("#card details.rating-edit summary").click()
    }

    @Test
    fun `a Homely score can be saved, shows in the Homely block at the bottom and can be removed`() {
        openWithKey()
        tapSuburb()
        assertThat(page.locator("#card details.rating-edit")).not().hasAttribute("open", "")
        assertThat(page.locator("#card input[name=stars]")).isHidden()
        openRatingForm()
        page.locator("#card input[name=stars]").fill("2.1")
        page.locator("#card input[name=reviews]").fill("7")
        page.locator("#card form.rating-form button[type=submit]").click()
        val row = page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Homely rating"))
        assertThat(row).containsText("2.1 ★ (7 reviews)")
        assertThat(row).containsText("Homely (entered by you)")
        assertEquals(2.1, app.rating?.stars)
        // The Homely block is the last section: after every data row, the rating, then the link to its reviews, then the edit link.
        val order = page.locator("#card dl.rows .row dt, #card a.external-link, #card details.rating-edit summary").allInnerTexts()
        val change = order.indexOfFirst { it.startsWith("Price change") }
        val score = order.indexOf("Homely rating")
        val link = order.indexOfFirst { it.startsWith("Reviews on Homely") }
        val edit = order.indexOf("Edit the Homely rating")
        assertTrue(change in 0 until score && score < link && link < edit, "order was $order")
        val lastDataRow = order.indexOfLast { !it.startsWith("Homely rating") && !it.startsWith("Reviews on Homely") && !it.startsWith("Edit the Homely") }
        assertTrue(lastDataRow < score, "the Homely block follows every data row: $order")
        assertThat(page.locator("#card details.rating-edit")).not().hasAttribute("open", "")

        openRatingForm()
        page.locator("#card button", Page.LocatorOptions().setHasText("Remove")).click()
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Homely rating"))).hasCount(0)
        assertEquals(null, app.rating)
    }

    @Test
    fun `a score out of range is refused with the reason and nothing is saved`() {
        openWithKey()
        tapSuburb()
        openRatingForm()
        page.locator("#card input[name=stars]").fill("9")
        page.locator("#card form.rating-form button[type=submit]").click()
        assertThat(page.locator("#card .rating-message")).hasText("Stars must be a number from 0 to 5")
        assertEquals(null, app.rating)
    }

    @Test
    fun `a failing save is reported beside the form`() {
        openWithKey()
        tapSuburb()
        app.failSavingRatings = true
        openRatingForm()
        page.locator("#card input[name=stars]").fill("3")
        page.locator("#card form.rating-form button[type=submit]").click()
        assertThat(page.locator("#card .rating-message")).containsText("Could not save")
    }

    @Test
    fun `the right-hand panel opens on Filters, shows the card on a tap and goes back when it is cleared`() {
        openWithKey()
        assertThat(page.locator("#tab-filters")).hasAttribute("aria-selected", "true")
        assertThat(page.locator("#filters")).isVisible()
        assertThat(page.locator("#card")).isHidden()
        page.locator("#filter-icsea-limit").fill("1000")
        assertThat(page.locator("#tab-filters")).hasText("Filters (1 of 3)")
        tapSuburb()
        assertThat(page.locator("#tab-suburb")).hasAttribute("aria-selected", "true")
        assertThat(page.locator("#tab-suburb")).hasText("Suburb")
        assertThat(page.locator("#filters")).isHidden()
        page.locator("#tab-filters").click()
        assertThat(page.locator("#filters")).isVisible()
        assertThat(page.locator("#filter-summary")).containsText("1 of 3 suburbs match")
        page.locator("#tab-suburb").click()
        assertThat(page.locator("#card h2")).hasText("HAMILTON")
        page.keyboard().press("Escape")
        assertThat(page.locator("#tab-filters")).hasAttribute("aria-selected", "true")
        // Tapping the open tab folds the panel down to its tabs, and again unfolds it.
        page.locator("#tab-filters").click()
        assertThat(page.locator("#filters")).isHidden()
        page.locator("#tab-filters").click()
        assertThat(page.locator("#filters")).isVisible()
    }

    private fun cardScrollTop(): Int = (page.evaluate("document.getElementById('card').scrollTop") as Number).toInt()

    @Test
    fun `the card's header and Customise bar stay in view while the rows scroll`() {
        openWithKey()
        tapSuburb()
        showUnknownRows()
        page.evaluate("document.getElementById('card').scrollTop = 100000")
        assertTrue(cardScrollTop() > 0, "the card should be long enough to scroll")
        val offset = page.evaluate(
            "document.querySelector('#card .card-sticky').getBoundingClientRect().top - document.getElementById('card').getBoundingClientRect().top",
        ) as Number
        assertTrue(offset.toDouble() in -1.0..1.0, "the pinned block should sit at the top of the card, not $offset px away")
        assertThat(page.locator("#card .card-sticky h2")).isInViewport()
        assertThat(page.locator("#card .card-sticky button[data-action=customise]")).isInViewport()
        assertThat(page.locator("#card .card-sticky button.close")).isInViewport()
    }

    @Test
    fun `reset all switches every filter off and puts the sliders back`() {
        openWithKey()
        assertThat(page.locator("#filter-reset")).isDisabled()
        val start = page.locator("#filter-icsea-limit").inputValue()
        page.locator("#filter-icsea-limit").fill("1000")
        assertThat(page.locator("#filter-icsea-on")).isChecked()
        assertThat(page.locator("#tab-filters")).containsText("of 3)")
        assertThat(page.locator("#filter-reset")).isEnabled()
        page.locator("#filter-reset").click()
        assertThat(page.locator("#filter-icsea-on")).not().isChecked()
        assertThat(page.locator("#filter-icsea-limit")).hasValue(start)
        assertThat(page.locator("#filter-summary")).containsText("Switch on a filter")
        assertThat(page.locator("#tab-filters")).hasText("Filters")
        assertThat(page.locator("#filter-reset")).isDisabled()
    }

    @Test
    fun `choosing another suburb keeps the card scrolled to the same place`() {
        openWithKey()
        tapSuburb()
        showUnknownRows()
        page.evaluate("document.getElementById('card').scrollTop = 400")
        val before = cardScrollTop()
        assertTrue(before > 100, "the card should be long enough to scroll ($before)")
        page.locator("#search-input").fill("may")
        assertThat(page.locator("#search-results [role=option]")).hasCount(1)
        page.locator("#search-input").press("Enter")
        assertThat(page.locator("#card h2")).hasText("MAYFIELD")
        assertThat(page.locator("#card dl.rows .row").first()).isVisible()
        assertEquals(before, cardScrollTop(), "the same rows should still be in view for the new suburb")
        // Closing the card and opening one from nothing starts at the top.
        page.keyboard().press("Escape")
        tapSuburb()
        assertEquals(0, cardScrollTop())
    }

    @Test
    fun `a metric scale bar is always on the map, changes with the zoom and is not covered by the panel`() {
        openWithKey()
        val scale = page.locator(".maplibregl-ctrl-scale")
        assertThat(scale).isVisible()
        assertThat(scale).hasText(Pattern.compile("""\d+(\.\d+)?\s?(m|km)"""))
        val before = scale.innerText()
        page.locator(".maplibregl-ctrl-zoom-in").click()
        page.locator(".maplibregl-ctrl-zoom-in").click()
        assertThat(scale).not().hasText(before)
        // Open, folded and with a card, on a wide window, it never sits under the right-hand panel.
        fun clearOfPanel() {
            val bar = checkNotNull(scale.boundingBox())
            val panel = checkNotNull(page.locator("#side").boundingBox())
            assertTrue(bar.x + bar.width <= panel.x || bar.y + bar.height <= panel.y || bar.x >= panel.x + panel.width || bar.y >= panel.y + panel.height, "the scale bar is under the panel")
        }
        clearOfPanel()
        // On a wide window it sits at the bottom, left of the panel's left edge and clear of the map credit.
        fun bottomLeftOfPanel() {
            val bar = checkNotNull(scale.boundingBox())
            val panel = checkNotNull(page.locator("#side").boundingBox())
            val credit = checkNotNull(page.locator(".maplibregl-ctrl-attrib").boundingBox())
            val height = checkNotNull(page.viewportSize()).height
            assertTrue(bar.y > height / 2, "the scale bar is not at the bottom: ${bar.y}")
            assertTrue(bar.x + bar.width <= panel.x, "the scale bar is not left of the panel: right edge ${bar.x + bar.width}, panel ${panel.x}")
            assertTrue(bar.x + bar.width <= credit.x || bar.x >= credit.x + credit.width || bar.y + bar.height <= credit.y || bar.y >= credit.y + credit.height, "the scale bar overlaps the map credit")
        }
        bottomLeftOfPanel()
        // Folded to its tab strip the panel leaves the bar where it is, and unfolded again too.
        page.locator("#tab-filters").click()
        assertThat(scale).isVisible()
        clearOfPanel()
        bottomLeftOfPanel()
        page.locator("#tab-filters").click()
        bottomLeftOfPanel()
        tapSuburb()
        assertThat(scale).isVisible()
        clearOfPanel()
        // On a narrow window the panel is a bottom sheet (an open one can cover the bar).
        page.setViewportSize(400, 700)
        assertThat(scale).isVisible()
        // Folded to its tab strip, the sheet leaves the bar visible just above it, clear of the map credit.
        page.locator("#tab-suburb").click()
        val bar = checkNotNull(scale.boundingBox())
        val sheet = checkNotNull(page.locator("#side").boundingBox())
        val credit = checkNotNull(page.locator(".maplibregl-ctrl-attrib").boundingBox())
        assertTrue(bar.y + bar.height <= sheet.y, "the scale bar is under the folded sheet: bar bottom ${bar.y + bar.height}, sheet top ${sheet.y}")
        assertTrue(bar.x >= 0 && bar.x + bar.width <= checkNotNull(page.viewportSize()).width, "the scale bar is off screen")
        assertTrue(bar.x + bar.width <= credit.x || bar.x >= credit.x + credit.width || bar.y + bar.height <= credit.y || bar.y >= credit.y + credit.height, "the scale bar overlaps the map credit")
    }

    @Test
    fun `rows without data are left off the card, counted in a note, and can be shown and hidden again`() {
        openWithKey()
        tapSuburb()
        assertThat(page.locator("#card .value.unknown")).hasCount(0)
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Transport"))).hasCount(0)
        assertThat(page.locator("#card .unknown-note")).containsText("rows without data are hidden.")
        // The Homely block is at the bottom, after the rows and the note.
        val order = page.locator("#card dl.rows .row dt, #card details.rating-edit summary").allInnerTexts()
        assertTrue(order.indexOf("Price change, 12 months") < order.indexOf("Edit the Homely rating"), "order was $order")
        assertTrue(order.last() == "Edit the Homely rating", "the Homely block is last: $order")
        page.locator("#card button[data-action=show-unknown]").click()
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Transport"))).hasCount(1)
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Transport"))).containsText("Not computed yet")
        assertThat(page.locator("#card .unknown-note")).containsText("Showing")
        // Customising lists every row either way, with no note.
        page.locator("#card button[data-action=customise]").click()
        assertThat(page.locator("#card .unknown-note")).hasCount(0)
        page.locator("#card button[data-action=customise-done]").click()
        page.locator("#card button[data-action=hide-unknown]").click()
        assertThat(page.locator("#card .value.unknown")).hasCount(0)
    }
}
