package au.homefinder.web

import au.homefinder.app.api.ApiConfig
import au.homefinder.app.api.FilterApi
import au.homefinder.app.filter.FilterData
import au.homefinder.app.filter.FilterSelection
import au.homefinder.app.filter.IcseaRange
import au.homefinder.app.ui.FILTER_LOADING_TEXT
import au.homefinder.app.ui.FILTER_UNAVAILABLE_TEXT
import au.homefinder.app.ui.FilterIds
import au.homefinder.app.ui.filterPanel
import au.homefinder.app.ui.filterSummaryText
import au.homefinder.app.api.KtorHttp
import au.homefinder.app.api.LotApi
import au.homefinder.app.api.LookupResult
import au.homefinder.app.api.RatingApi
import au.homefinder.app.api.SearchApi
import au.homefinder.app.api.SuburbApi
import au.homefinder.app.selection.LotCardState
import au.homefinder.app.selection.MapViewModel
import au.homefinder.app.selection.RatingOutcome
import au.homefinder.app.selection.SearchViewModel
import au.homefinder.app.selection.SuburbCardState
import au.homefinder.app.ui.CardActions
import au.homefinder.app.ui.RatingForm
import au.homefinder.app.ui.SearchIds
import au.homefinder.app.ui.lotCard
import au.homefinder.app.ui.searchSuggestions
import au.homefinder.app.ui.suburbCard
import au.homefinder.shared.model.SearchResult
import au.homefinder.shared.model.SearchResultKind
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import au.homefinder.app.selection.SearchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.html.div
import kotlinx.html.dom.append
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent

private const val KEY_STORAGE = "homefinder.apiKey"
private const val ESCAPE = "Escape"
private const val NOTICE_MILLIS = 4_000
private const val ARROW_DOWN = "ArrowDown"
private const val ARROW_UP = "ArrowUp"
private const val ENTER = "Enter"
private const val NO_LOT_NOTICE = "No lot found for this address"

private fun element(id: String): HTMLElement = document.getElementById(id) as HTMLElement

private fun storedKey(): String? = runCatching { localStorage.getItem(KEY_STORAGE) }.getOrNull()?.takeIf { it.isNotBlank() }

private fun storeKey(key: String) {
    runCatching { localStorage.setItem(KEY_STORAGE, key) }
}

fun main() {
    window.onload = {
        val key = storedKey()
        if (key != null) start(key) else askForKey()
    }
}

/** The key is entered once and kept in this browser (ADR 0019); it is never put in a URL. */
private fun askForKey() {
    val prompt = element("key-prompt")
    prompt.hidden = false
    (element("key-form") as HTMLFormElement).onsubmit = { event ->
        event.preventDefault()
        val key = (element("key-input") as HTMLInputElement).value.trim()
        if (key.isNotEmpty()) {
            storeKey(key)
            prompt.hidden = true
            start(key)
        }
    }
}

private fun start(apiKey: String) {
    val origin = window.location.origin
    val scope = MainScope()
    val config = ApiConfig(origin, apiKey)
    val http = KtorHttp()
    val ratingApi = RatingApi(config, http, http)
    val viewModel = MapViewModel(SuburbApi(config, http), scope, LotApi(config, http), ratingApi)
    val map = SuburbMap(origin, apiKey, viewModel::onMapTap)

    val card = element("card")
    val notice = element("notice")
    var shownNotice: String? = null

    fun showNotice(text: String) {
        notice.textContent = text
        notice.hidden = false
        window.setTimeout({ notice.hidden = true }, NOTICE_MILLIS)
    }

    wireSearch(scope, SearchApi(config, http), viewModel, map, ::showNotice)
    wireFilters(scope, FilterApi(config, http), map)

    // The star scores under suburb names: loaded at the start and again whenever one is saved or removed.
    suspend fun refreshRatings() {
        (ratingApi.all() as? LookupResult.Found)?.let { map.showRatings(it.value.ratings) }
    }
    viewModel.ratingsVersion.onEach { refreshRatings() }.launchIn(scope)

    viewModel.state.onEach { state ->
        map.showSelection(state.selectedSuburbId, state.selectedLotId)
        renderCard(card, state.card, state.lotCard)
        if (state.notice != shownNotice) {
            val text = state.notice
            shownNotice = text
            if (text != null) showNotice(text) else notice.hidden = true
        }
    }.launchIn(scope)

    // One listener for the buttons and one for the rating form, so re-rendering the card never loses them.
    card.addEventListener("click", { event ->
        when ((event.target as? HTMLElement)?.getAttribute(CardActions.ATTRIBUTE)) {
            CardActions.CLOSE -> viewModel.clear()
            CardActions.RETRY -> viewModel.retry()
            CardActions.REMOVE_RATING -> scope.launch { announce(viewModel.removeRating(), card, ::showNotice) }
        }
    })
    card.addEventListener("submit", { event ->
        val form = event.target as? HTMLFormElement
        if (form?.hasAttribute(RatingForm.ATTRIBUTE) == true) {
            event.preventDefault()
            val stars = (form.elements.namedItem(RatingForm.STARS_FIELD) as? HTMLInputElement)?.value.orEmpty()
            val reviews = (form.elements.namedItem(RatingForm.REVIEWS_FIELD) as? HTMLInputElement)?.value.orEmpty()
            scope.launch { announce(viewModel.saveRating(stars, reviews), card, ::showNotice) }
        }
    })
    document.addEventListener("keydown", { event ->
        // Escape closes an open suggestion list first; the selection is cleared only when no list is showing.
        if ((event as KeyboardEvent).key == ESCAPE && !searchIsOpen) viewModel.clear()
    })
}

private var searchIsOpen = false

/**
 * The filter panel (ADR 0035): the values load once, the inputs are drawn once, and moving a slider only recomputes which
 * suburbs pass and restyles the map, with no request.
 */
private fun wireFilters(scope: CoroutineScope, api: FilterApi, view: SuburbMap) {
    val toggle = element("filters-toggle")
    val panel = element("filters")
    var data: FilterData? = null

    fun input(id: String) = document.getElementById(id) as? HTMLInputElement

    fun selection(): FilterSelection {
        val on = input(FilterIds.ICSEA_ON)?.checked == true
        val low = input(FilterIds.ICSEA_MIN)?.value?.toIntOrNull()
        val high = input(FilterIds.ICSEA_MAX)?.value?.toIntOrNull()
        return if (on && low != null && high != null) FilterSelection(IcseaRange(minOf(low, high), high)) else FilterSelection()
    }

    fun refresh() {
        val loaded = data ?: return
        val result = loaded.apply(selection())
        view.showFilter(result.passingIds)
        document.getElementById(FilterIds.SUMMARY)?.textContent = filterSummaryText(result)
        input(FilterIds.ICSEA_MIN)?.let { document.getElementById(FilterIds.ICSEA_MIN_OUT)?.textContent = it.value }
        input(FilterIds.ICSEA_MAX)?.let { document.getElementById(FilterIds.ICSEA_MAX_OUT)?.textContent = it.value }
    }

    toggle.addEventListener("click", {
        panel.hidden = !panel.hidden
        toggle.setAttribute("aria-expanded", (!panel.hidden).toString())
    })
    panel.addEventListener("input", { event ->
        // The sliders never cross: dragging one past the other pulls the other along.
        val low = input(FilterIds.ICSEA_MIN)
        val high = input(FilterIds.ICSEA_MAX)
        val target = (event.target as? HTMLElement)?.id
        if (low != null && high != null && low.value.toInt() > high.value.toInt()) {
            if (target == FilterIds.ICSEA_MIN) high.value = low.value else low.value = high.value
        }
        refresh()
    })

    panel.textContent = FILTER_LOADING_TEXT
    scope.launch {
        when (val result = api.values()) {
            is LookupResult.Found -> {
                val loaded = FilterData.from(result.value)
                data = loaded
                panel.innerHTML = ""
                panel.append { div { filterPanel(loaded, loaded.apply(FilterSelection())) } }
            }
            else -> panel.textContent = FILTER_UNAVAILABLE_TEXT
        }
    }
}

/** The search box: typing, the keyboard, a click on a suggestion, and what each kind of result does to the map. */
private fun wireSearch(scope: CoroutineScope, api: SearchApi, map: MapViewModel, view: SuburbMap, showNotice: (String) -> Unit) {
    val search = SearchViewModel(api, scope)
    val input = element("search-input") as HTMLInputElement
    val results = element("search-results")

    fun go(result: SearchResult) {
        when (result.kind) {
            SearchResultKind.SUBURB -> {
                result.bounds?.let(view::fitTo)
                map.select(result.id)
            }
            SearchResultKind.ADDRESS -> {
                view.flyToAddress(result.latitude, result.longitude)
                val lotId = result.lotId
                if (lotId != null) {
                    map.selectLot(lotId)
                } else {
                    map.clear()
                    showNotice(NO_LOT_NOTICE)
                }
            }
        }
    }

    search.state.onEach { state ->
        searchIsOpen = state.isOpen && state.status !is SearchStatus.Idle
        if (input.value != state.query) input.value = state.query
        input.setAttribute("aria-expanded", searchIsOpen.toString())
        if (state.highlighted >= 0 && searchIsOpen) input.setAttribute("aria-activedescendant", SearchIds.option(state.highlighted))
        else input.removeAttribute("aria-activedescendant")
        results.innerHTML = ""
        results.append { div { searchSuggestions(state) } }
        results.querySelector(".highlighted")?.scrollIntoView(js("({block: 'nearest'})"))
    }.launchIn(scope)

    input.addEventListener("input", { search.onQueryChanged(input.value) })
    input.addEventListener("focus", { search.open() })
    input.addEventListener("keydown", { event ->
        event as KeyboardEvent
        when (event.key) {
            ARROW_DOWN -> { event.preventDefault(); search.moveHighlight(1) }
            ARROW_UP -> { event.preventDefault(); search.moveHighlight(-1) }
            ENTER -> { event.preventDefault(); search.choose()?.let(::go); input.blur() }
            ESCAPE -> if (searchIsOpen) { event.preventDefault(); search.close() }
        }
    })
    // mousedown, not click: the input would lose focus and the list close before a click lands.
    results.addEventListener("mousedown", { event ->
        val index = ((event.target as? HTMLElement)?.closest("[${SearchIds.INDEX_ATTRIBUTE}]") as? HTMLElement)
            ?.getAttribute(SearchIds.INDEX_ATTRIBUTE)?.toIntOrNull()
        if (index != null) {
            event.preventDefault()
            search.choose(index)?.let(::go)
        }
    })
    results.addEventListener("mouseover", { event ->
        ((event.target as? HTMLElement)?.closest("[${SearchIds.INDEX_ATTRIBUTE}]") as? HTMLElement)
            ?.getAttribute(SearchIds.INDEX_ATTRIBUTE)?.toIntOrNull()?.let(search::highlight)
    })
    document.addEventListener("mousedown", { event ->
        if ((event.target as? HTMLElement)?.closest("#search") == null) search.close()
    })
}

/** Shows the lot card when a lot is selected, else the suburb card; at most one is ever visible. */
private fun renderCard(container: HTMLElement, suburb: SuburbCardState, lot: LotCardState) {
    container.hidden = suburb is SuburbCardState.Hidden && lot is LotCardState.Hidden
    container.innerHTML = ""
    when {
        lot !is LotCardState.Hidden -> container.append { div { lotCard(lot) } }
        suburb !is SuburbCardState.Hidden -> container.append { div { suburbCard(suburb) } }
    }
}

/** Says how saving or removing a score went: success as a notice (the card has just been redrawn), a problem beside the form. */
private fun announce(outcome: RatingOutcome, card: HTMLElement, showNotice: (String) -> Unit) {
    when (outcome) {
        RatingOutcome.Saved -> showNotice("Homely rating saved")
        RatingOutcome.Removed -> showNotice("Homely rating removed")
        is RatingOutcome.Rejected -> card.querySelector(".${RatingForm.MESSAGE_CLASS}")?.textContent = outcome.message
        is RatingOutcome.Failed -> card.querySelector(".${RatingForm.MESSAGE_CLASS}")?.textContent = "Could not save: ${outcome.message}"
    }
}
