package au.homefinder.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExternalLinksTest {
    @Test
    fun `a suburb becomes Homely's profile address`() {
        assertEquals("https://www.homely.com.au/suburb-profile/jesmond-nsw-2299", ExternalLinks.homelySuburbProfile("JESMOND", "2299"))
        assertEquals("https://www.homely.com.au/suburb-profile/hamilton-north-nsw-2292", ExternalLinks.homelySuburbProfile("HAMILTON NORTH", "2292"))
    }

    @Test
    fun `punctuation and spacing in a name become single hyphens`() {
        assertEquals("https://www.homely.com.au/suburb-profile/windermere-park-nsw-2264", ExternalLinks.homelySuburbProfile("  Windermere   Park ", "2264"))
        assertEquals("https://www.homely.com.au/suburb-profile/st-georges-basin-nsw-2540", ExternalLinks.homelySuburbProfile("St. George's Basin", "2540"))
    }

    @Test
    fun `a missing or malformed postcode, or an empty name, gives no link`() {
        assertNull(ExternalLinks.homelySuburbProfile("JESMOND", null))
        assertNull(ExternalLinks.homelySuburbProfile("JESMOND", ""))
        assertNull(ExternalLinks.homelySuburbProfile("JESMOND", "22"))
        assertNull(ExternalLinks.homelySuburbProfile("JESMOND", "22x9"))
        assertNull(ExternalLinks.homelySuburbProfile("---", "2299"))
    }

    @Test
    fun `a name cannot add anything to the address`() {
        val url = ExternalLinks.homelySuburbProfile("a/../b?x=1#y", "2299")
        assertEquals("https://www.homely.com.au/suburb-profile/a-b-x-1-y-nsw-2299", url)
    }

    @Test
    fun `only an http or https address may be linked`() {
        assertEquals("https://a.example/x", ExternalLinks.webAddress(" https://a.example/x "))
        assertEquals("http://a.example", ExternalLinks.webAddress("http://a.example"))
        listOf("javascript:alert(1)", "data:text/html,x", "//a.example", "ftp://a.example", "a.example", "", null).forEach { assertNull(ExternalLinks.webAddress(it), it.toString()) }
    }
}
