package au.homefinder.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Route
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import au.homefinder.shared.model.NaplanScore
import kotlinx.datetime.LocalDate
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
        app.naplan.clear()
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

    /** The fake suburb has rows with no data, which the card leaves off; Customise lists every row, which makes the card long enough to scroll (ADR 0058). */
    private fun showUnknownRows() {
        page.locator("#card button[data-action=customise]").click()
        assertThat(page.locator("#card button[data-action=customise-done]")).isVisible()
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
    fun `the filter wash and tint fade out by the zoom where lot outlines start`() {
        openWithKey()
        assertThat(page.locator("#map canvas")).isVisible()
        // The layers are added when the style has loaded, which on a slow runner is after the canvas shows.
        page.waitForFunction("window.homefinderMap && window.homefinderMap.getLayer('suburb-filter-match') && window.homefinderMap.getLayer('suburb-filter-dim')")
        for (layer in listOf("suburb-filter-match", "suburb-filter-dim")) {
            val opacity = page.evaluate("JSON.stringify(window.homefinderMap.getPaintProperty('$layer', 'fill-opacity'))").toString()
            assertEquals("""["interpolate",["linear"],["zoom"],15,1,16,0]""", opacity, layer)
        }
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
    fun `the one Schools row names the zoned schools and lists each with its share of the suburb, ICSEA, own website and SchoolRank link`() {
        openWithKey()
        tapSuburb()
        val rows = page.locator("#card dl.rows .row:has(.zones)")
        assertThat(page.locator("#card dl.rows dt", Page.LocatorOptions().setHasText("Zoned schools"))).hasCount(0)
        // The group heading says "Schools"; the list under it has no second heading.
        assertThat(page.locator("#card dl.rows dt", Page.LocatorOptions().setHasText(Pattern.compile("^Schools$")))).hasCount(0)
        assertThat(page.locator("#card summary", Page.LocatorOptions().setHasText(Pattern.compile("^Schools$")))).hasCount(1)
        val row = rows.first()
        assertThat(row).not().containsText("Hamilton South Public School (primary), Newcastle High School (high)")
        assertThat(row).containsText("Hamilton South Public School")
        assertThat(row).not().containsText("public school, average ICSEA")
        // Line by line (ADR 0071): name and zone, ICSEA, NAPLAN, then the links.
        assertThat(page.locator("#card ul.zones")).containsText("primary, K-6 · 82% of the suburb's lots")
        assertThat(page.locator("#card ul.zones")).containsText("high, 7-12 · 76% of the suburb's lots")
        assertThat(page.locator("#card ul.zones .school-icsea")).hasText(arrayOf("ICSEA 1,041", "ICSEA 1,017"))
        assertThat(page.locator("#card ul.schools")).hasCount(0)
        page.locator("#card details.card-group[data-group='Schools'] > summary").click()
        // My School is linked only from the NAPLAN form.
        assertThat(page.locator("#card a[href='https://www.myschool.edu.au/']")).hasCount(0)
        // One closed "Details" per row holds the note and the source (ADR 0072); there is no separate Source fold.
        val details = row.locator("details.details-fold")
        assertThat(details).hasCount(1)
        assertThat(details).not().hasAttribute("open", "")
        assertThat(row.locator("details.source-details, details.note-details")).hasCount(0)
        // The fold has no value line to sit under, so it follows the list of schools instead of floating above it (ADR 0083).
        assertThat(row.locator("ul.zones + dd.row-details details.details-fold")).hasCount(1)
        details.locator("summary").click()
        assertThat(details.locator("p.details-source")).containsText("Source: Test catchments")
        assertThat(details.locator("p.details-note")).containsText("Catchments change")
        // A SchoolRank link built from the school's name and locality (ADR 0070); never fetched. The zoned school without a locality gets none.
        page.waitForFunction("() => document.querySelectorAll('#card a[href^=\"https://schoolrank.com.au/school/\"]').length > 0")
        val rank = page.locator("#card ul.zones a[href='https://schoolrank.com.au/school/hamilton-south-public-school-hamilton-nsw']")
        assertThat(rank).hasText("SchoolRank")
        assertThat(rank).hasAttribute("target", "_blank")
        assertThat(rank).hasAttribute("rel", "noopener noreferrer")
        assertThat(rank).isVisible()
        assertThat(page.locator("#card ul.zones a", Page.LocatorOptions().setHasText("SchoolRank"))).hasCount(1)
    }

    @Test
    fun `a NAPLAN difference from the national average can be added to a school, shows on its own line under its ICSEA and can be cleared`() {
        openWithKey()
        tapSuburb()
        page.locator("#card details.card-group[data-group='Schools'] > summary").click()
        val edit = page.locator("#naplan-edit-2")
        assertThat(edit).hasText("Add NAPLAN")
        assertThat(page.locator("#naplan-form")).hasCount(0)
        edit.click()
        // The form opens under Newcastle High School, the second zone, and only there.
        assertThat(page.locator("#card ul.zones li", Page.LocatorOptions().setHasText("Newcastle High School")).locator("#naplan-form")).isVisible()
        assertThat(page.locator("#naplan-clear")).hasCount(0)
        page.locator("#naplan-score").fill("-1200")
        page.locator("#naplan-save").click()
        assertThat(page.locator("#naplan-message")).hasText("The difference must be a whole number from -1000 to +1000")
        assertTrue(app.naplan.isEmpty())
        page.locator("#naplan-score").fill("-41")
        page.locator("#naplan-save").click()
        page.waitForFunction("() => document.getElementById('naplan-form') === null && document.querySelector('#card ul.zones')?.textContent.includes('NAPLAN -41 vs national')")
        // The figure has its own line under the ICSEA, with the Edit button beside it (ADR 0071).
        val newcastle = page.locator("#card ul.zones li", Page.LocatorOptions().setHasText("Newcastle High School"))
        assertThat(newcastle.locator(".naplan-line .naplan-figure")).hasText("NAPLAN -41 vs national")
        assertThat(newcastle.locator(".school-icsea")).hasText("ICSEA 1,017")
        assertEquals(-41, app.naplan["2"]?.score)
        assertThat(page.locator("#naplan-edit-2")).hasText("Edit NAPLAN")
        // The row's Source says it is your own figure, without claiming where it came from (ADR 0069).
        assertThat(page.locator("#card dl.rows .row:has(.zones)").first()).containsText("Your own figure, typed in.")

        // There is no year at all (ADR 0073): no box in the form, none on the card, none sent.
        page.locator("#naplan-edit-2").click()
        assertThat(page.locator("#naplan-year")).hasCount(0)
        assertThat(page.locator("#naplan-form")).not().containsText("Year")
        page.locator("#naplan-save").click()
        page.waitForFunction("() => document.getElementById('naplan-form') === null && document.querySelector('#card ul.zones')?.textContent.includes('NAPLAN -41 vs national') && !document.querySelector('#card ul.zones')?.textContent.includes('NAPLAN -41 vs national (')")

        // Opening it again fills the boxes; Cancel closes it without a change; Clear removes the score.
        page.locator("#naplan-edit-2").click()
        assertThat(page.locator("#naplan-score")).hasValue("-41")
        assertThat(page.locator("#naplan-note")).hasCount(0)
        page.locator("#naplan-cancel").click()
        assertThat(page.locator("#naplan-form")).hasCount(0)
        page.locator("#naplan-edit-2").click()
        page.locator("#naplan-clear").click()
        page.waitForFunction("() => document.getElementById('naplan-form') === null && !document.querySelector('#card ul.zones')?.textContent.includes('vs national')")
        assertThat(page.locator("#naplan-edit-2")).hasText("Add NAPLAN")
        assertTrue(app.naplan.isEmpty())
    }

    /** The NAPLAN label features the map holds now, as "suburb:text" (ADR 0076). */
    private fun naplanLabels(): String =
        "[...new Set(window.homefinderMap.querySourceFeatures('suburb-naplan').map(f => f.properties.suburb_id + ':' + f.properties.label))].sort().join(',')"

    @Test
    fun `each suburb with a NAPLAN average gets a blue diamond label that opens its Schools group, and saving a figure redraws the labels`() {
        // Hamilton: (0.82 x 30 + 0.76 x -10) / 1.58 = 10.8, so +11; Mayfield is all in school 2's zone, so -10; Wickham has no figure and no label.
        app.naplan["1"] = NaplanScore(30, LocalDate(2026, 11, 7))
        app.naplan["2"] = NaplanScore(-10, LocalDate(2026, 11, 8))
        openWithKey()
        page.waitForFunction("() => window.homefinderMap && window.homefinderMap.getLayer('suburb-naplan-label') && window.homefinderMap.getLayer('suburb-rated-label')")
        // Its own colour and a diamond icon, unlike the Homely label's brown stars; shown from the Homely label's zoom until lots take over.
        val map = "window.homefinderMap"
        assertEquals("\"#0d47a1\"", page.evaluate("JSON.stringify($map.getPaintProperty('suburb-naplan-label', 'text-color'))").toString())
        assertTrue("#8a4b00" in page.evaluate("JSON.stringify($map.getLayoutProperty('suburb-rated-label', 'text-field'))").toString())
        assertEquals("\"naplan-diamond\"", page.evaluate("JSON.stringify($map.getLayoutProperty('suburb-naplan-label', 'icon-image'))").toString())
        assertEquals("undefined", page.evaluate("String($map.getLayoutProperty('suburb-rated-label', 'icon-image'))").toString())
        assertEquals("true", page.evaluate("String($map.hasImage('naplan-diamond'))").toString())
        assertEquals("11,16", page.evaluate("[$map.getLayer('suburb-naplan-label').minzoom, $map.getLayer('suburb-naplan-label').maxzoom].join(',')").toString())

        page.evaluate("() => { $map.jumpTo({center: [151.75, -32.92], zoom: 13}); }")
        page.waitForFunction("() => (${naplanLabels()}) === '9001:+11,9002:-10'")
        // The diamond ends 2 px left of the label point, 17 px below it (under a one-line name); click its middle with the real mouse.
        val at = "(() => { const p = $map.project([151.75, -32.92]); return [p.x - 2 - 6, p.y + 17]; })()"
        page.waitForFunction("() => { const [x, y] = $at; return $map.queryRenderedFeatures([x, y], {layers: ['suburb-naplan-label']}).length > 0 }")
        @Suppress("UNCHECKED_CAST")
        val point = page.evaluate(at) as List<Number>
        val box = requireNotNull(page.locator("#map canvas").boundingBox())
        page.mouse().click(box.x + point[0].toDouble(), box.y + point[1].toDouble())
        assertThat(page.locator("#card h2")).hasText("HAMILTON")
        assertThat(page.locator("#tab-suburb")).hasAttribute("aria-selected", "true")
        val schools = page.locator("#card details.card-group[data-group='Schools']")
        assertThat(schools).hasAttribute("open", "open")
        // Scrolled in just under the pinned header.
        page.waitForFunction(
            "() => { const card = document.getElementById('card'); const g = card.querySelector(\"details.card-group[data-group='Schools']\"); " +
                "const sticky = card.querySelector('.card-sticky').getBoundingClientRect(); const top = g.getBoundingClientRect().top; " +
                "return top >= sticky.bottom - 1 && top <= sticky.bottom + 40 }",
        )

        // The keyboard way: the card's Jump to Schools button does the same after the group is shut and the card scrolled back up.
        page.evaluate("document.querySelector(\"#card details.card-group[data-group='Schools']\").open = false; document.getElementById('card').scrollTop = 0")
        assertThat(schools).not().hasAttribute("open", "open")
        page.locator("#card button[data-action=jump-schools]").focus()
        page.keyboard().press("Enter")
        assertThat(schools).hasAttribute("open", "open")
        page.waitForFunction("() => document.getElementById('card').scrollTop > 0")

        // Clearing school 2's figure on the card redraws the labels without a reload: Hamilton rests on school 1 alone, Mayfield has none.
        page.locator("#naplan-edit-2").click()
        page.locator("#naplan-clear").click()
        page.waitForFunction("() => (${naplanLabels()}) === '9001:+30'")
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

    /** Folds every filter group open (ADR 0066: only the first starts open) so the criteria can be reached. */
    /** The filter panel is drawn only once its values arrive from the API; anything that reads or opens its groups must wait for that first. */
    private fun waitForFilterPanel() {
        page.waitForFunction("() => document.querySelectorAll('#filters details.filter-group').length > 0")
    }

    private fun openFilterGroups() {
        waitForFilterPanel()
        page.evaluate("document.querySelectorAll('#filters details.filter-group').forEach(d => d.open = true)")
        page.waitForFunction("() => [...document.querySelectorAll('#filters details.filter-group')].every(d => d.open)")
    }

    @Test
    fun `filter groups fold, the first starts open, and a group counts its switched-on filters`() {
        openWithKey()
        waitForFilterPanel()
        assertTrue(page.evaluate("document.getElementById('filter-group-people-and-area').open") == true)
        assertTrue(page.evaluate("document.getElementById('filter-group-schools').open") == false)
        assertThat(page.locator("#filter-zoned-on")).not().isVisible()
        page.locator("#filter-group-schools > summary").click()
        assertThat(page.locator("#filter-zoned-on")).isVisible()
        page.locator("#filter-zoned-on").check()
        assertThat(page.locator("#filter-group-schools-count")).hasText("1 on")
        page.locator("#filter-reset").click()
        assertThat(page.locator("#filter-group-schools-count")).hasText("")
    }

    @Test
    fun `the PDF export is off until a filter shades suburbs, then downloads them sorted by name with the filters used`() {
        app.lastExport = null
        openWithKey()
        openFilterGroups()
        val button = page.locator("#filter-export")
        assertThat(button).isDisabled()
        assertThat(button).hasText("Export PDF")
        assertThat(page.locator("#filter-export-hint")).containsText("Switch on a filter")
        page.locator("#filter-zoned-on").check()
        assertThat(button).isEnabled()
        assertThat(button).hasText("Export PDF (2 suburbs)")
        val download = page.waitForDownload { button.click() }
        assertTrue(Regex("""homefinder-suburbs-\d{4}-\d{2}-\d{2}\.pdf""").matches(download.suggestedFilename()), download.suggestedFilename())
        assertThat(page.locator("#filter-export-status")).containsText("Downloaded homefinder-suburbs-")
        val sent = requireNotNull(app.lastExport)
        assertEquals(listOf("9001", "9002"), sent.suburbIds)
        assertEquals(listOf("Zoned schools' ICSEA: At least 990"), sent.filters)
        // At 1,030 only HAMILTON passes; Reset all switches the filters off and the export with them.
        page.locator("#filter-zoned-limit").fill("1030")
        assertThat(button).hasText("Export PDF (1 suburb)")
        page.locator("#filter-reset").click()
        assertThat(button).isDisabled()
    }

    @Test
    fun `the filter panel counts matching suburbs as the zoned-school ICSEA slider moves`() {
        openWithKey()
        openFilterGroups()
        assertThat(page.locator("#tab-filters")).hasAttribute("aria-selected", "true")
        assertThat(page.locator("#filter-summary")).containsText("Switch on a filter")
        assertThat(page.locator("#filter-zoned-unknown")).containsText("1 of 3 suburbs have no zoned-school ICSEA")
        assertThat(page.locator("#filters")).containsText("not a measure of school quality")
        page.locator("#filter-zoned-on").check()
        assertThat(page.locator("#filter-summary")).containsText("2 of 3 suburbs match")
        // One slider, "at least": the limit starts at the lowest zoned-school ICSEA, so switching on changed nothing.
        assertThat(page.locator("#filter-zoned-limit-value")).hasText("990")
        assertThat(page.locator("#filter-zoned-min")).hasCount(0)
        page.locator("#filter-zoned-limit").fill("1000")
        assertThat(page.locator("#filter-zoned-limit-value")).hasText("1000")
        assertThat(page.locator("#filter-summary")).containsText("1 of 3 suburbs match")
        page.locator("#filter-zoned-on").uncheck()
        assertThat(page.locator("#filter-summary")).containsText("Switch on a filter")
    }

    @Test
    fun `filters on the share rented and the beach distance combine, and the sliders show their own units`() {
        openWithKey()
        openFilterGroups()
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
    fun `the zoned NAPLAN filter counts suburbs as its slider moves, shows in the tab and the group count, and Reset all puts it back`() {
        // Hamilton: (0.82 x 30 + 0.76 x -10) / 1.58 = +11; Mayfield: school 2 only, -10; Wickham has no zoned school with a figure.
        app.naplan["1"] = NaplanScore(30, LocalDate(2026, 11, 7))
        app.naplan["2"] = NaplanScore(-10, LocalDate(2026, 11, 8))
        openWithKey()
        openFilterGroups()
        assertThat(page.locator("#filter-naplan-on")).isEnabled()
        assertThat(page.locator("#filter-naplan-limit-value")).hasText("-10")
        assertThat(page.locator("#filter-naplan-limit")).hasAttribute("min", "-10")
        assertThat(page.locator("#filter-naplan-limit")).hasAttribute("max", "11")
        assertThat(page.locator("#filter-naplan-unknown")).containsText("1 of 3 suburbs have no NAPLAN figure for a zoned school")
        assertThat(page.locator("#filters")).containsText("2 of 3 suburbs have at least one.")
        page.locator("#filter-naplan-on").check()
        assertThat(page.locator("#filter-summary")).containsText("2 of 3 suburbs match")
        assertThat(page.locator("#filter-group-schools-count")).hasText("1 on")
        page.locator("#filter-naplan-limit").fill("0")
        assertThat(page.locator("#filter-naplan-limit-value")).hasText("0")
        assertThat(page.locator("#filter-summary")).containsText("1 of 3 suburbs match")
        assertThat(page.locator("#tab-filters")).hasText("Filters (1 of 3)")
        page.locator("#filter-naplan-limit").fill("11")
        assertThat(page.locator("#filter-naplan-limit-value")).hasText("+11")
        page.locator("#filter-reset").click()
        assertThat(page.locator("#filter-naplan-on")).not().isChecked()
        assertThat(page.locator("#filter-naplan-limit")).hasValue("-10")
        assertThat(page.locator("#filter-group-schools-count")).hasText("")
        assertThat(page.locator("#filter-summary")).containsText("Switch on a filter")
        assertThat(page.locator("#tab-filters")).hasText("Filters")
    }

    @Test
    fun `with no NAPLAN figure typed the filter cannot be switched on, and saving one on the card brings its slider in`() {
        openWithKey()
        openFilterGroups()
        assertThat(page.locator("#filter-naplan-limit-value")).hasText("No NAPLAN figures typed yet")
        assertThat(page.locator("#filter-naplan-on")).isDisabled()
        assertThat(page.locator("#filter-naplan-limit")).isDisabled()
        assertThat(page.locator("#filters")).containsText("0 of 3 suburbs have at least one.")
        // Another filter stays switched on across the reload.
        page.locator("#filter-zoned-limit").fill("1000")
        assertThat(page.locator("#filter-summary")).containsText("1 of 3 suburbs match")
        tapSuburb()
        page.locator("#card details.card-group[data-group='Schools'] > summary").click()
        page.locator("#naplan-edit-2").click()
        page.locator("#naplan-score").fill("-41")
        page.locator("#naplan-save").click()
        page.waitForFunction("() => document.getElementById('naplan-form') === null")
        page.waitForFunction("() => { const on = document.getElementById('filter-naplan-on'); return on !== null && !on.disabled }")
        page.locator("#tab-filters").click()
        assertThat(page.locator("#filter-naplan-limit-value")).hasText("-41")
        assertThat(page.locator("#filters")).containsText("2 of 3 suburbs have at least one.")
        assertThat(page.locator("#filter-zoned-on")).isChecked()
        assertThat(page.locator("#filter-zoned-limit")).hasValue("1000")
        assertThat(page.locator("#filter-summary")).containsText("1 of 3 suburbs match")
        // Clearing it takes the slider away again.
        page.locator("#tab-suburb").click()
        page.locator("#naplan-edit-2").click()
        page.locator("#naplan-clear").click()
        page.waitForFunction("() => document.getElementById('filter-naplan-on')?.disabled === true")
        assertTrue(app.naplan.isEmpty())
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
        openFilterGroups()
        page.locator("#filter-zoned-limit").fill("1000")
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
        // In Customise mode (which the test is in to make the card long) the bar holds Done, not Customise.
        assertThat(page.locator("#card .card-sticky button[data-action=customise-done]")).isInViewport()
        assertThat(page.locator("#card .card-sticky button.close")).isInViewport()
    }

    @Test
    fun `reset all switches every filter off and puts the sliders back`() {
        openWithKey()
        openFilterGroups()
        assertThat(page.locator("#filter-reset")).isDisabled()
        val start = page.locator("#filter-zoned-limit").inputValue()
        page.locator("#filter-zoned-limit").fill("1000")
        assertThat(page.locator("#filter-zoned-on")).isChecked()
        assertThat(page.locator("#tab-filters")).containsText("of 3)")
        assertThat(page.locator("#filter-reset")).isEnabled()
        page.locator("#filter-reset").click()
        assertThat(page.locator("#filter-zoned-on")).not().isChecked()
        assertThat(page.locator("#filter-zoned-limit")).hasValue(start)
        assertThat(page.locator("#filter-summary")).containsText("Switch on a filter")
        assertThat(page.locator("#tab-filters")).hasText("Filters")
        assertThat(page.locator("#filter-reset")).isDisabled()
    }

    @Test
    fun `choosing another suburb keeps the card scrolled to the same place`() {
        openWithKey()
        tapSuburb()
        showUnknownRows()
        page.evaluate("document.getElementById('card').scrollTop = 200")
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
    fun `a compass is always visible, big enough to tap, and turns with the map`() {
        openWithKey()
        val compass = page.locator(".maplibregl-ctrl-compass")
        assertThat(compass).isVisible()
        assertThat(compass).hasAttribute("aria-label", Pattern.compile("Compass"))
        page.waitForFunction("() => { const r = document.querySelector('.maplibregl-ctrl-compass').getBoundingClientRect(); return r.width >= 44 && r.height >= 44 }")
        page.locator(".maplibregl-ctrl-zoom-in").click()
        assertThat(compass).isVisible()
        // Turned, the arrow turns the other way and stays on screen; clicking it puts north back up.
        page.evaluate("() => { window.homefinderMap.setBearing(40) }")
        page.waitForFunction("() => document.querySelector('.maplibregl-ctrl-compass .maplibregl-ctrl-icon').style.transform.includes('-40')")
        assertThat(compass).isVisible()
        // It is the red arrow with an N, not MapLibre's small grey one (a screenshot showed that once), and turned it stays inside its 44 px button.
        val drawing = page.evaluate("() => getComputedStyle(document.querySelector('.maplibregl-ctrl-compass .maplibregl-ctrl-icon')).backgroundImage") as String
        assertTrue(drawing.contains("%23d32f2f") && drawing.contains("%3EN%3C"), "the compass is not the red north arrow with an N: $drawing")
        val button = checkNotNull(compass.boundingBox())
        val box = checkNotNull(page.locator(".maplibregl-ctrl-compass .maplibregl-ctrl-icon").boundingBox())
        val view = checkNotNull(page.viewportSize())
        assertTrue(box.x >= button.x - 0.5 && box.y >= button.y - 0.5 && box.x + box.width <= button.x + button.width + 0.5 && box.y + box.height <= button.y + button.height + 0.5, "the turned compass overflows its button: $box in $button")
        assertTrue(box.x >= 0 && box.y >= 0 && box.x + box.width <= view.width && box.y + box.height <= view.height, "the compass is off screen")
        compass.click()
        page.waitForFunction("() => Math.abs(window.homefinderMap.getBearing()) < 0.5")
        // Beside the zoom buttons, not over them; on a narrow window too.
        fun clearOfZoom() {
            val c = checkNotNull(compass.boundingBox())
            val zoomOut = checkNotNull(page.locator(".maplibregl-ctrl-zoom-out").boundingBox())
            assertTrue(c.y >= zoomOut.y + zoomOut.height - 1, "the compass overlaps the zoom buttons")
        }
        clearOfZoom()
        page.setViewportSize(400, 700)
        assertThat(compass).isVisible()
        clearOfZoom()
        page.locator("#tab-suburb").click()
        assertThat(compass).isVisible()
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
        // Tapped again the sheet opens: at most half the window, with the bar and credit kept above it (ADR 0067).
        page.locator("#tab-suburb").click()
        val height = checkNotNull(page.viewportSize()).height
        val open = checkNotNull(page.locator("#side").boundingBox())
        assertTrue(open.height <= height / 2 + 1, "the open sheet is taller than half the window: ${open.height} of $height")
        val openBar = checkNotNull(scale.boundingBox())
        val openCredit = checkNotNull(page.locator(".maplibregl-ctrl-attrib").boundingBox())
        assertTrue(openBar.y + openBar.height <= open.y, "the scale bar is under the open sheet")
        assertTrue(openCredit.y + openCredit.height <= open.y + 1, "the map credit is under the open sheet")
    }

    @Test
    fun `rows without data are left off the card, with no note, and Customise lists them`() {
        openWithKey()
        tapSuburb()
        assertThat(page.locator("#card .value.unknown")).hasCount(0)
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Transport"))).hasCount(0)
        assertThat(page.locator("#card .unknown-note")).hasCount(0)
        // The Homely block is at the bottom, after the rows and the note.
        val order = page.locator("#card dl.rows .row dt, #card details.rating-edit summary").allInnerTexts()
        assertTrue(order.indexOf("Price change, 12 months") < order.indexOf("Edit the Homely rating"), "order was $order")
        assertTrue(order.last() == "Edit the Homely rating", "the Homely block is last: $order")
        // Customising lists every row, unknown ones included, with their reason.
        page.locator("#card button[data-action=customise]").click()
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Transport"))).hasCount(1)
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Transport"))).containsText("Not computed yet")
        page.locator("#card button[data-action=customise-done]").click()
        assertThat(page.locator("#card .value.unknown")).hasCount(0)
    }
}
