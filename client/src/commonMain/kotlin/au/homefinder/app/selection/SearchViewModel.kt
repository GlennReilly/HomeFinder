package au.homefinder.app.selection

import au.homefinder.app.api.LookupResult
import au.homefinder.app.api.SearchApi
import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.model.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the suggestion list says besides the suggestions themselves. */
sealed interface SearchStatus {
    /** Nothing to show: no query, or too short a one. */
    data object Idle : SearchStatus
    data object Searching : SearchStatus
    data object Ready : SearchStatus
    data object NoMatches : SearchStatus
    data class Failed(val message: String) : SearchStatus
}

/**
 * The search box's state. [highlighted] is the suggestion Enter would choose (-1 when none); [isOpen] says whether the
 * list is showing, so Escape can close it without clearing what was typed.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val highlighted: Int = NONE,
    val status: SearchStatus = SearchStatus.Idle,
    val isOpen: Boolean = false,
) {
    companion object {
        const val NONE: Int = -1
    }
}

/**
 * Drives the search box (ADR 0026). A search starts [debounceMillis] after the last keystroke, so typing a word is one
 * request; starting a new one cancels the old, so a slow earlier answer can never replace a newer one.
 */
class SearchViewModel(
    private val api: SearchApi,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
) {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private var job: Job? = null

    fun onQueryChanged(text: String) {
        job?.cancel()
        val query = text.trim()
        if (query.length < ApiContract.SEARCH_MIN_QUERY_LENGTH) {
            mutableState.value = SearchUiState(query = text)
            return
        }
        // Earlier suggestions stay on screen while the next ones load, so the list does not flicker.
        mutableState.update { it.copy(query = text, status = SearchStatus.Searching, isOpen = true) }
        job = scope.launch {
            delay(debounceMillis)
            mutableState.value = when (val answer = api.search(query)) {
                is LookupResult.Found -> {
                    val results = answer.value.results
                    if (results.isEmpty()) {
                        SearchUiState(query = text, status = SearchStatus.NoMatches, isOpen = true)
                    } else {
                        SearchUiState(query = text, results = results, highlighted = 0, status = SearchStatus.Ready, isOpen = true)
                    }
                }
                LookupResult.Unauthorized -> failed(text, MESSAGE_KEY_REJECTED)
                LookupResult.NotFound -> failed(text, MESSAGE_FAILED)
                is LookupResult.Failed -> failed(text, MESSAGE_FAILED)
            }
        }
    }

    private fun failed(text: String, message: String) =
        SearchUiState(query = text, status = SearchStatus.Failed(message), isOpen = true)

    /** Moves the highlight up (-1) or down (1), wrapping round the ends. */
    fun moveHighlight(delta: Int) {
        mutableState.update { state ->
            if (state.results.isEmpty()) return@update state
            val size = state.results.size
            val from = if (state.highlighted == SearchUiState.NONE) (if (delta > 0) -1 else 0) else state.highlighted
            state.copy(highlighted = ((from + delta) % size + size) % size, isOpen = true)
        }
    }

    fun highlight(index: Int) {
        mutableState.update { if (index in it.results.indices) it.copy(highlighted = index) else it }
    }

    /** Reopens the suggestions that were showing (when the box is focused again). */
    fun open() {
        mutableState.update { if (it.results.isNotEmpty() || it.status is SearchStatus.Failed) it.copy(isOpen = true) else it }
    }

    /** Closes the list but keeps what was typed. */
    fun close() {
        mutableState.update { it.copy(isOpen = false) }
    }

    /**
     * Chooses a suggestion (the highlighted one by default) and returns it, or null when there is none. The list closes
     * and the box shows the chosen label.
     */
    fun choose(index: Int = mutableState.value.highlighted): SearchResult? {
        val state = mutableState.value
        val result = state.results.getOrNull(index) ?: return null
        job?.cancel()
        mutableState.value = SearchUiState(query = result.label)
        return result
    }

    companion object {
        const val DEBOUNCE_MILLIS: Long = 200
        const val MESSAGE_KEY_REJECTED: String = "The API key was rejected"
        const val MESSAGE_FAILED: String = "Search is unavailable"
    }
}
