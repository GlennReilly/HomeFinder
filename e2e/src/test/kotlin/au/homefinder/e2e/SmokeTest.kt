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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import kotlin.test.assertEquals
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
    fun `tapping the map selects a suburb and shows its card, and Escape closes it`() {
        openWithKey()
        tapSuburb()
        assertThat(page.locator("#card")).containsText("Postcode 2303")
        assertThat(page.locator("#card dl.rows .row").first()).containsText("Area")
        assertThat(page.locator("#card a.external-link[href*='homely.com.au']")).hasAttribute("href", "https://www.homely.com.au/suburb-profile/hamilton-nsw-2303")
        page.keyboard().press("Escape")
        assertThat(page.locator("#card")).isHidden()
    }

    @Test
    fun `the Schools row lists the public schools with their ICSEA and own website, and links to My School`() {
        openWithKey()
        tapSuburb()
        val row = page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Schools").setHasNotText("Flood"))
        assertThat(row.first()).containsText("1 public school, average ICSEA 1,051 (1,000 is average)")
        assertThat(page.locator("#card ul.schools a")).hasAttribute("href", "https://hamilton-p.schools.nsw.gov.au")
        assertThat(page.locator("#card ul.schools")).containsText("ICSEA 1,051 · 412 students")
        assertThat(page.locator("#card a[href='https://www.myschool.edu.au/']")).isVisible()
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
        assertThat(page.locator("#card")).containsText("Block size")
        assertThat(page.locator("#card")).containsText("Moderate, 12.0% on average (steepest 21.3%)")
        assertThat(page.locator("#card")).containsText("35.0 m")

        page.locator("#search-input").fill("120 main st")
        page.locator("#search-results [role=option]").click()
        assertThat(page.locator("#notice")).hasText("No lot found for this address")
        assertThat(page.locator("#card")).isHidden()
    }

    @Test
    fun `the filter panel counts matching suburbs as the ICSEA slider moves`() {
        openWithKey()
        page.locator("#filters-toggle").click()
        assertThat(page.locator("#filters-toggle")).hasAttribute("aria-expanded", "true")
        assertThat(page.locator("#filter-summary")).containsText("Switch on a filter")
        assertThat(page.locator("#filter-summary")).containsText("1 of 3 suburbs have no ICSEA")
        assertThat(page.locator("#filters")).containsText("not a measure of school quality")
        page.locator("#filter-icsea-on").check()
        assertThat(page.locator("#filter-summary")).containsText("2 of 3 suburbs match")
        page.locator("#filter-icsea-min").fill("1000")
        assertThat(page.locator("#filter-icsea-min-value")).hasText("1000")
        assertThat(page.locator("#filter-summary")).containsText("1 of 3 suburbs match")
        page.locator("#filter-icsea-on").uncheck()
        assertThat(page.locator("#filter-summary")).containsText("Switch on a filter")
    }

    @Test
    fun `search says so when nothing matches`() {
        openWithKey()
        page.locator("#search-input").fill("zzz")
        assertThat(page.locator("#search-results")).containsText("No matches")
    }

    @Test
    fun `a Homely score can be saved, shows under the price change and can be removed`() {
        openWithKey()
        tapSuburb()
        page.locator("#card input[name=stars]").fill("2.1")
        page.locator("#card input[name=reviews]").fill("7")
        page.locator("#card form.rating-form button[type=submit]").click()
        val row = page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Homely rating"))
        assertThat(row).containsText("2.1 ★ (7 reviews)")
        assertThat(row).containsText("Homely (entered by you)")
        assertEquals(2.1, app.rating?.stars)
        // The rows read in the order the card promises: price change, the link, the rating, then its form.
        val order = page.locator("#card dl.rows .row dt, #card a.external-link, #card form.rating-form h3").allInnerTexts()
        val change = order.indexOfFirst { it.startsWith("Price change") }
        val link = order.indexOfFirst { it.startsWith("Reviews on Homely") }
        val score = order.indexOf("Homely rating")
        val form = order.indexOf("Your Homely rating")
        assertTrue(change in 0 until link && link < score && score < form, "order was $order")

        page.locator("#card button", Page.LocatorOptions().setHasText("Remove")).click()
        assertThat(page.locator("#card dl.rows .row", Page.LocatorOptions().setHasText("Homely rating"))).hasCount(0)
        assertEquals(null, app.rating)
    }

    @Test
    fun `a score out of range is refused with the reason and nothing is saved`() {
        openWithKey()
        tapSuburb()
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
        page.locator("#card input[name=stars]").fill("3")
        page.locator("#card form.rating-form button[type=submit]").click()
        assertThat(page.locator("#card .rating-message")).containsText("Could not save")
    }
}
