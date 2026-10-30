package au.homefinder.app.ui

import au.homefinder.app.selection.SearchStatus
import au.homefinder.app.selection.SearchUiState
import au.homefinder.shared.model.GeoBounds
import au.homefinder.shared.model.SearchResult
import au.homefinder.shared.model.SearchResultKind
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchSuggestionsHtmlTest {
    private val suburb = SearchResult(SearchResultKind.SUBURB, "9001", "HAMILTON", "Suburb, NSW 2303", -32.92, 151.75, GeoBounds(151.74, -32.93, 151.76, -32.91))
    private val linked = SearchResult(SearchResultKind.ADDRESS, "G1", "12 MAIN STREET, HAMILTON NSW 2303", null, -32.92, 151.75, lotId = "1001")
    private val unlinked = SearchResult(SearchResultKind.ADDRESS, "G2", "120 MAIN STREET, HAMILTON NSW 2303", null, -32.92, 151.75)

    private fun html(state: SearchUiState) = createHTML().div { searchSuggestions(state) }

    private fun ready(highlighted: Int = 0) =
        SearchUiState("ham", listOf(suburb, linked, unlinked), highlighted, SearchStatus.Ready, isOpen = true)

    @Test
    fun `suggestions are a listbox of options with their kind, label and detail`() {
        val out = html(ready())
        assertTrue("role=\"listbox\"" in out)
        assertEquals(3, Regex("role=\"option\"").findAll(out).count())
        assertTrue("<span class=\"kind\">Suburb</span><span class=\"label\">HAMILTON</span><span class=\"detail\">Suburb, NSW 2303</span>" in out)
        assertTrue("12 MAIN STREET, HAMILTON NSW 2303" in out)
        assertTrue("data-index=\"2\"" in out)
    }

    @Test
    fun `only the highlighted option is selected`() {
        val out = html(ready(highlighted = 1))
        assertEquals(1, Regex("aria-selected=\"true\"").findAll(out).count())
        assertEquals(1, Regex("suggestion highlighted").findAll(out).count())
    }

    @Test
    fun `an address without a lot says so, one with a lot adds nothing`() {
        val out = html(ready())
        assertEquals(1, Regex("No lot known for this address").findAll(out).count())
    }

    @Test
    fun `closed or idle shows nothing`() {
        assertEquals("<div></div>", html(ready().copy(isOpen = false)).trim())
        assertEquals("<div></div>", html(SearchUiState()).trim())
    }

    @Test
    fun `searching, no matches and failure are plain messages`() {
        assertTrue("Searching…" in html(SearchUiState("ham", status = SearchStatus.Searching, isOpen = true)))
        assertTrue("No matches" in html(SearchUiState("zzz", status = SearchStatus.NoMatches, isOpen = true)))
        val failed = html(SearchUiState("ham", status = SearchStatus.Failed("Search is unavailable"), isOpen = true))
        assertTrue("Search is unavailable" in failed && "role=\"alert\"" in failed)
    }

    @Test
    fun `earlier suggestions stay while the next search runs`() {
        val out = html(ready().copy(status = SearchStatus.Searching))
        assertTrue("HAMILTON" in out && "Searching…" in out)
    }

    @Test
    fun `text from the API is escaped`() {
        val hostile = linked.copy(label = "<script>alert(1)</script>")
        assertFalse("<script>" in html(SearchUiState("x", listOf(hostile), 0, SearchStatus.Ready, true)))
    }
}
