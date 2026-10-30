package au.homefinder.app.ui

import au.homefinder.app.selection.SearchStatus
import au.homefinder.app.selection.SearchUiState
import au.homefinder.shared.model.SearchResult
import au.homefinder.shared.model.SearchResultKind
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.span
import kotlinx.html.ul

/** Names the page uses to find the pieces of the suggestion list. */
object SearchIds {
    const val LIST: String = "search-results-list"
    const val OPTION_PREFIX: String = "search-option-"
    const val INDEX_ATTRIBUTE: String = "data-index"

    fun option(index: Int): String = "$OPTION_PREFIX$index"
}

/** The words beside a suggestion that say what kind it is. */
fun kindLabel(kind: SearchResultKind): String = when (kind) {
    SearchResultKind.SUBURB -> "Suburb"
    SearchResultKind.ADDRESS -> "Address"
}

/** The second line of a suggestion: a suburb says "Suburb, NSW 2303", an address says whether a lot is known. */
fun suggestionDetail(result: SearchResult): String? = when (result.kind) {
    SearchResultKind.SUBURB -> result.detail
    SearchResultKind.ADDRESS -> if (result.lotId == null) "No lot known for this address" else result.detail
}

/**
 * The suggestion list as plain HTML: an ARIA listbox whose options carry their index, so one click listener can choose
 * them. Shows nothing while closed or idle, and a plain message while searching, with no matches or on failure.
 */
fun FlowContent.searchSuggestions(state: SearchUiState) {
    if (!state.isOpen || state.status is SearchStatus.Idle) return
    if (state.results.isNotEmpty()) {
        ul("suggestions") {
            id = SearchIds.LIST
            attributes["role"] = "listbox"
            attributes["aria-label"] = "Search suggestions"
            state.results.forEachIndexed { index, result ->
                li(if (index == state.highlighted) "suggestion highlighted" else "suggestion") {
                    id = SearchIds.option(index)
                    attributes["role"] = "option"
                    attributes["aria-selected"] = (index == state.highlighted).toString()
                    attributes[SearchIds.INDEX_ATTRIBUTE] = index.toString()
                    span("kind") { +kindLabel(result.kind) }
                    span("label") { +result.label }
                    suggestionDetail(result)?.let { span("detail") { +it } }
                }
            }
        }
    }
    when (val status = state.status) {
        SearchStatus.Searching -> div("search-status") { attributes["role"] = "status"; +"Searching…" }
        SearchStatus.NoMatches -> div("search-status") { attributes["role"] = "status"; +"No matches" }
        is SearchStatus.Failed -> div("search-status error") { attributes["role"] = "alert"; +status.message }
        else -> Unit
    }
}
