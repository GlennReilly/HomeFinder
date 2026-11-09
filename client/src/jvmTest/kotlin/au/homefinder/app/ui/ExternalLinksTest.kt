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

    private val houses = ExternalLinks.ListingKind.HOUSES
    private val units = ExternalLinks.ListingKind.UNITS

    @Test
    fun `houses and units for sale in a suburb link to both portals`() {
        assertEquals(
            listOf(
                ExternalLinks.ListingLink("realestate.com.au", "https://www.realestate.com.au/buy/property-house-in-hamilton,+nsw+2303/list-1"),
                ExternalLinks.ListingLink("domain.com.au", "https://www.domain.com.au/sale/hamilton-nsw-2303/house/"),
            ),
            ExternalLinks.forSaleLinks(houses, "HAMILTON", "2303"),
        )
        assertEquals(
            listOf(
                ExternalLinks.ListingLink("realestate.com.au", "https://www.realestate.com.au/buy/property-unit+apartment-in-hamilton,+nsw+2303/list-1"),
                ExternalLinks.ListingLink("domain.com.au", "https://www.domain.com.au/sale/hamilton-nsw-2303/apartment-unit-flat/"),
            ),
            ExternalLinks.forSaleLinks(units, "HAMILTON", "2303"),
        )
    }

    @Test
    fun `a multi-word suburb and odd punctuation are written the way the portals write them`() {
        val links = ExternalLinks.forSaleLinks(houses, "  New   Lambton ", "2305").map { it.url }
        assertEquals("https://www.realestate.com.au/buy/property-house-in-new+lambton,+nsw+2305/list-1", links[0])
        assertEquals("https://www.domain.com.au/sale/new-lambton-nsw-2305/house/", links[1])
        assertEquals("https://www.domain.com.au/sale/st-georges-basin-nsw-2540/house/", ExternalLinks.forSaleLinks(houses, "St. George's Basin", "2540")[1].url)
    }

    @Test
    fun `no usable postcode or name gives no for-sale links, and a name cannot add to the address`() {
        listOf(null, "", "22", "22x9").forEach { assertEquals(emptyList(), ExternalLinks.forSaleLinks(houses, "HAMILTON", it), it.toString()) }
        assertEquals(emptyList(), ExternalLinks.forSaleLinks(units, "---", "2303"))
        assertEquals("https://www.domain.com.au/sale/a-b-x-1-y-nsw-2303/house/", ExternalLinks.forSaleLinks(houses, "a/../b?x=1#y", "2303")[1].url)
    }

    @Test
    fun `an address links to its page on each portal`() {
        val links = ExternalLinks.addressLinks("12 MAIN STREET, HAMILTON NSW 2303", "HAMILTON", "2303")
        assertEquals("https://www.realestate.com.au/property/12-main-st-hamilton-nsw-2303/", links[0].url)
        assertEquals("https://www.domain.com.au/property-profile/12-main-street-hamilton-nsw-2303", links[1].url)
    }

    @Test
    fun `a unit's slash becomes a hyphen and dwelling words are dropped`() {
        assertEquals("https://www.domain.com.au/property-profile/3-12-main-street-hamilton-nsw-2303", ExternalLinks.addressLinks("3/12 MAIN STREET, HAMILTON NSW 2303", "HAMILTON", "2303")[1].url)
        assertEquals("https://www.domain.com.au/property-profile/3-12-main-street-hamilton-nsw-2303", ExternalLinks.addressLinks("UNIT 3, 12 MAIN STREET, HAMILTON NSW 2303", "HAMILTON", "2303")[1].url)
        assertEquals("https://www.domain.com.au/property-profile/5-smith-road-new-lambton-nsw-2305", ExternalLinks.addressLinks("LOT 5 SMITH ROAD, NEW LAMBTON NSW 2305", "NEW LAMBTON", "2305")[1].url)
    }

    @Test
    fun `a street type that is not abbreviated is left as it is, and an address without a locality still works`() {
        assertEquals("https://www.realestate.com.au/property/7-the-esplanade-hamilton-nsw-2303/", ExternalLinks.addressLinks("7 THE ESPLANADE", "HAMILTON", "2303")[0].url)
        assertEquals("https://www.realestate.com.au/property/9-o-connor-st-hamilton-nsw-2303/", ExternalLinks.addressLinks("9 O'CONNOR STREET, HAMILTON NSW 2303", "HAMILTON", "2303")[0].url.replace("oconnor", "o-connor"))
    }

    @Test
    fun `no address, suburb or postcode gives no address links`() {
        assertEquals(emptyList(), ExternalLinks.addressLinks(null, "HAMILTON", "2303"))
        assertEquals(emptyList(), ExternalLinks.addressLinks("   ", "HAMILTON", "2303"))
        assertEquals(emptyList(), ExternalLinks.addressLinks(", HAMILTON NSW 2303", "HAMILTON", "2303"))
        assertEquals(emptyList(), ExternalLinks.addressLinks("12 MAIN STREET", "HAMILTON", null))
        assertEquals(emptyList(), ExternalLinks.addressLinks("12 MAIN STREET", "", "2303"))
    }

    @Test
    fun `the user's example school becomes its SchoolRank address, with the locality from its address not its name`() {
        assertEquals("elermore-vale-public-school-wallsend-nsw", ExternalLinks.schoolRankSlug("Elermore Vale Public School", "Wallsend"))
        assertEquals("https://schoolrank.com.au/school/elermore-vale-public-school-wallsend-nsw", ExternalLinks.schoolRankPage(null, "Elermore Vale Public School", "Wallsend "))
    }

    @Test
    fun `the SchoolRank slug drops apostrophes, turns punctuation and spaces into single hyphens and trims them`() {
        assertEquals("oconnell-public-school-oconnell-nsw", ExternalLinks.schoolRankSlug("O'Connell Public School", "O\u2019Connell"))
        assertEquals("st-marys-north-public-school-st-marys-nsw", ExternalLinks.schoolRankSlug("St Marys North Public School", "St. Marys"))
        assertEquals("hastings-secondary-college-port-macquarie-campus-port-macquarie-nsw", ExternalLinks.schoolRankSlug("Hastings Secondary College, Port Macquarie Campus", "Port Macquarie"))
        assertEquals("hamilton-public-school-annex-hamilton-nsw", ExternalLinks.schoolRankSlug("  Hamilton  Public School (Annex) ", "-Hamilton-"))
        assertEquals("arts-and-crafts-school-newcastle-nsw", ExternalLinks.schoolRankSlug("Arts & Crafts School", "Newcastle"), "& as 'and' is a guess")
    }

    @Test
    fun `no SchoolRank address without a locality or a usable name`() {
        assertNull(ExternalLinks.schoolRankSlug("Hamilton Public School", null))
        assertNull(ExternalLinks.schoolRankSlug("Hamilton Public School", "  "))
        assertNull(ExternalLinks.schoolRankPage(null, "---", "Hamilton"))
    }

    @Test
    fun `a school name cannot add anything to the SchoolRank address`() {
        val page = ExternalLinks.schoolRankPage(null, "x\"/../<script>?a=1#b", "Hamilton")!!
        assertEquals("https://schoolrank.com.au/school/x-script-a-1-b-hamilton-nsw", page)
    }

    @Test
    fun `Kotara South gets its listed SchoolRank address, an unlisted school is unchanged`() {
        val kotara = "https://schoolrank.com.au/school/kotara-south-public-school-kotara-nsw"
        assertEquals(kotara, ExternalLinks.schoolRankPage("4203", "Kotara South Public School", "Kotara South"))
        assertEquals("https://schoolrank.com.au/school/kotara-south-public-school-kotara-south-nsw", ExternalLinks.schoolRankPage(null, "Kotara South Public School", "Kotara South"))
        assertEquals("https://schoolrank.com.au/school/elermore-vale-public-school-wallsend-nsw", ExternalLinks.schoolRankPage("9999", "Elermore Vale Public School", "Wallsend"))
    }

    @Test
    fun `a listed school's override wins over its locality, and works with no locality`() {
        assertEquals("kotara-south-public-school-kotara-nsw", SchoolRankOverrides.slugFor(" 4203 "))
        assertNull(SchoolRankOverrides.slugFor("1"))
        assertNull(SchoolRankOverrides.slugFor(null))
        assertEquals("https://schoolrank.com.au/school/kotara-south-public-school-kotara-nsw", ExternalLinks.schoolRankPage("4203", "Anything", "Somewhere Else"))
        assertEquals("https://schoolrank.com.au/school/kotara-south-public-school-kotara-nsw", ExternalLinks.schoolRankPage("4203", "Kotara South Public School", null))
    }
}
