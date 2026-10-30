package au.homefinder.app.ui

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

    private fun html(card: LotCardState) = createHTML().div { lotCard(card) }

    @Test
    fun `a loaded lot shows its address, suburb, parcel, measured area and unknown factors`() {
        val out = html(LotCardState.Loaded(report(listOf("12 MAIN STREET, HAMILTON NSW 2303"))))
        assertTrue("<h2>12 MAIN STREET, HAMILTON NSW 2303</h2>" in out)
        assertTrue("HAMILTON 2303 · Parcel 1//DP100" in out)
        assertTrue("561 m²" in out)
        assertEquals(11, Regex("""class="value unknown"""").findAll(out).count())
        assertTrue("Source: cadastre" in out)
        assertTrue(CARD_DISCLAIMER in out)
        assertFalse("<ul" in out, "one address needs no list")
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
}
