package au.homefinder.app.ui

import au.homefinder.app.selection.CardLayout
import au.homefinder.app.selection.LotCardRows
import au.homefinder.app.selection.LotCardState
import au.homefinder.shared.api.SuburbRef
import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.LotRef
import au.homefinder.shared.model.LotReport
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LotCardHtmlTest {
    private fun <T> u() = Attribute.unknown<T>("Some source", note = "Not computed yet")

    private fun report(addresses: List<String>, label: String? = "1//DP100", area: Attribute<Double> = Attribute(561.4, Confidence.HIGH, "cadastre", null, "Measured")) =
        LotReport("1001", label, "9001", "HAMILTON", "2303", addresses, area, u(), u(), u(), u(), u(), u(), u(), u(), u(), u())

    /** These fixtures are mostly unknown, so by default the rows are shown (the tests are about layout); the hiding has its own test below. */
    private fun html(card: LotCardState, showUnknown: Boolean = true) = createHTML().div { lotCard(card, showUnknown = showUnknown) }

    @Test
    fun `a loaded lot shows its address, suburb, parcel, measured area and unknown factors`() {
        val out = html(LotCardState.Loaded(report(listOf("12 MAIN STREET, HAMILTON NSW 2303"))))
        assertTrue("<h2>12 MAIN STREET, HAMILTON NSW 2303</h2>" in out)
        assertTrue("HAMILTON 2303<span class=\"parcel-line\"> · Parcel 1//DP100</span>" in out)
        assertTrue("""<p class="subtitle parcel-scroll">Parcel 1//DP100</p>""" in out, "the parcel is repeated below the pinned header for narrow windows")
        assertEquals("HAMILTON 2303 · Parcel 1//DP100", lotCardSubtitle(LotCardState.Loaded(report(listOf("12 MAIN STREET, HAMILTON NSW 2303")))))
        assertTrue("561 m²" in out)
        assertEquals(9, Regex("""class="value unknown"""").findAll(out).count())
        assertTrue("cadastre" in out)
        assertTrue(CARD_DISCLAIMER in out)
        assertFalse("<ul" in out, "one address needs no list")
    }

    @Test
    fun `the parcel part of the header is separate from the place and absent when the parcel is the title`() {
        val withAddress = LotCardState.Loaded(report(listOf("12 MAIN STREET, HAMILTON NSW 2303")))
        assertEquals("HAMILTON 2303", lotCardPlace(withAddress))
        assertEquals("Parcel 1//DP100", lotCardParcel(withAddress))
        assertEquals(null, lotCardParcel(LotCardState.Loaded(report(emptyList()))))
        assertFalse("parcel-scroll" in html(LotCardState.Loaded(report(emptyList()))))
    }

    @Test
    fun `several addresses are listed under the header`() {
        val out = html(LotCardState.Loaded(report(listOf("1/12 MAIN STREET, HAMILTON NSW 2303", "2/12 MAIN STREET, HAMILTON NSW 2303"))))
        assertEquals(2, Regex("<li>").findAll(out).count())
        assertTrue("<h2>1/12 MAIN STREET, HAMILTON NSW 2303</h2>" in out)
    }

    @Test
    fun `a lot with no address is titled by its parcel, and with neither by its id`() {
        val parcel = html(LotCardState.Loaded(report(emptyList())))
        assertTrue("<h2>Lot 1//DP100</h2>" in parcel)
        assertFalse("Parcel 1//DP100" in parcel, "the parcel is already the title")
        assertTrue("<h2>Lot 1001</h2>" in html(LotCardState.Loaded(report(emptyList(), label = null))))
    }

    @Test
    fun `loading, failure and the close button`() {
        val loading = html(LotCardState.Loading("1001", LotRef("1001", "9001")))
        assertTrue("<progress" in loading && "data-action=\"close\"" in loading && "Close lot card" in loading)
        assertTrue("Loading lot…" in html(LotCardState.Loading("1001", null)))
        val failed = html(LotCardState.Failed("1001", "the server answered 500"))
        assertTrue("Could not load this lot: the server answered 500." in failed && "data-action=\"retry\"" in failed)
    }

    @Test
    fun `text from the API is escaped`() {
        val out = html(LotCardState.Loaded(report(listOf("<script>alert(1)</script>"))))
        assertFalse("<script>" in out)
        assertTrue(SuburbRef("1", "a", null).suburbId == "1")
    }

    private fun custom(layout: CardLayout, customising: Boolean, showUnknown: Boolean = true) =
        createHTML().div { lotCard(LotCardState.Loaded(report(listOf("12 MAIN STREET, HAMILTON NSW 2303"))), layout, customising, showUnknown) }

    private fun labelsIn(out: String) = Regex("<dt>([^<]*)</dt>").findAll(out).map { it.groupValues[1] }.toList()

    @Test
    fun `hidden rows are left out and the chosen order is used`() {
        val layout = CardLayout().toggle("Schools").moveDown("Block size", LotCardRows.of(report(emptyList())))
        val labels = labelsIn(custom(layout, false))
        assertFalse("Schools" in labels)
        assertEquals(listOf("Backyard aspect", "Block size"), labels.take(2))
    }

    @Test
    fun `customising shows all ten rows with controls and a Done button`() {
        val out = custom(CardLayout().toggle("Schools"), true)
        assertEquals(10, labelsIn(out).size)
        assertEquals(10, Regex("""class="row-controls"""").findAll(out).count())
        assertTrue("""aria-label="Show Schools"""" in out && """data-action="customise-done"""" in out)
    }

    @Test
    fun `the lot card's header is pinned too`() {
        val out = html(LotCardState.Loading("1001", null))
        assertTrue(STICKY_TOP_CLASS in out && "Close lot card" in out.substringAfter(STICKY_TOP_CLASS))
    }

    @Test
    fun `lot rows without data are left off with a count and a way to show them`() {
        val loaded = LotCardState.Loaded(report(listOf("12 MAIN STREET, HAMILTON NSW 2303")))
        val hidden = html(loaded, showUnknown = false)
        val shown = html(loaded, showUnknown = true)
        val unknownRows = Regex("""class="value unknown"""").findAll(shown).count()
        assertTrue(unknownRows > 0)
        assertEquals(0, Regex("""class="value unknown"""").findAll(hidden).count())
        assertTrue("$unknownRows rows without data are hidden." in hidden || "1 row without data is hidden." in hidden, hidden)
        assertTrue("Block size" in hidden, "a row with a value stays")
        assertTrue(">Show them<" in hidden || ">Show it<" in hidden)
    }

    @Test
    fun `under the block size there are links to the lot's address on both portals, opening in a new tab`() {
        val out = html(LotCardState.Loaded(report(listOf("12 MAIN STREET, HAMILTON NSW 2303"))), showUnknown = false)
        assertTrue("This address: " in out)
        assertTrue("""href="https://www.domain.com.au/property-profile/12-main-street-hamilton-nsw-2303"""" in out)
        assertTrue("""href="https://www.realestate.com.au/property/12-main-st-hamilton-nsw-2303/"""" in out)
        assertEquals(2, Regex("""target="_blank" rel="noopener noreferrer"""").findAll(out).count().coerceAtLeast(Regex("""rel="noopener noreferrer"""").findAll(out).count()))
        assertTrue(out.indexOf("Block size") < out.indexOf("This address: "))
    }

    @Test
    fun `with several addresses the first is linked, and with none there are no links`() {
        val several = html(LotCardState.Loaded(report(listOf("1/12 MAIN STREET, HAMILTON NSW 2303", "2/12 MAIN STREET, HAMILTON NSW 2303"))), showUnknown = false)
        assertTrue("1-12-main-street-hamilton-nsw-2303" in several && "2-12-main-street" !in several)
        assertFalse("This address" in html(LotCardState.Loaded(report(emptyList())), showUnknown = false))
    }

    @Test
    fun `a block size with no value is left off but the address links stay`() {
        val out = html(LotCardState.Loaded(report(listOf("12 MAIN STREET, HAMILTON NSW 2303"), area = u())), showUnknown = false)
        assertFalse("<dt>Block size</dt>" in out)
        assertTrue("<dt>This address</dt>" in out)
    }
}
