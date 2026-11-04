package au.homefinder.web

import au.homefinder.app.api.ApiConfig
import au.homefinder.app.api.FilterApi
import au.homefinder.app.filter.FilterCriterion
import au.homefinder.app.filter.FilterData
import au.homefinder.app.filter.FilterRange
import au.homefinder.app.filter.FilterSelection
import au.homefinder.app.filter.Preference
import au.homefinder.app.filter.ScoreSetup
import au.homefinder.app.filter.ScoreWeight
import au.homefinder.app.ui.ScoreIds
import au.homefinder.app.ui.scoreRankingLines
import au.homefinder.app.ui.scoreSummaryText
import org.w3c.dom.HTMLSelectElement
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
import au.homefinder.app.selection.CardLayout
import au.homefinder.app.selection.CardRow
import au.homefinder.app.selection.LotCardRows
import au.homefinder.app.selection.LotCardState
import au.homefinder.app.selection.MapUiState
import au.homefinder.app.selection.SuburbCardRows
import au.homefinder.app.selection.MapViewModel
import au.homefinder.app.selection.SIDE_EMPTY_TEXT
import au.homefinder.app.selection.SidePanelState
import au.homefinder.app.selection.SideTab
import au.homefinder.app.selection.filtersTabLabel
import au.homefinder.app.filter.FilterResult
import au.homefinder.app.selection.RatingOutcome
import au.homefinder.app.selection.SearchViewModel
import au.homefinder.app.selection.SuburbCardState
import au.homefinder.app.ui.CardActions
import au.homefinder.app.ui.RatingForm
import au.homefinder.app.ui.SearchIds
import au.homefinder.app.ui.LayoutActions
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
import kotlinx.html.p
import kotlinx.html.dom.append
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent

private const val KEY_STORAGE = "homefinder.apiKey"
private const val SUBURB_LAYOUT_STORAGE = "homefinder.layout.suburb"
private const val LOT_LAYOUT_STORAGE = "homefinder.layout.lot"
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
    val side = wireSidePanel()
    wireFilters(scope, FilterApi(config, http), map) { result -> side.setFiltersLabel(filtersTabLabel(result)) }

    // The star scores under suburb names: loaded at the start and again whenever one is saved or removed.
    suspend fun refreshRatings() {
        (ratingApi.all() as? LookupResult.Found)?.let { map.showRatings(it.value.ratings) }
    }
    viewModel.ratingsVersion.onEach { refreshRatings() }.launchIn(scope)

    val layouts = CardLayouts()
    var latest = MapUiState()
    fun redraw() = renderCard(card, latest.card, latest.lotCard, layouts)

    viewModel.state.onEach { state ->
        map.showSelection(state.selectedSuburbId, state.selectedLotId)
        if (state.selectedSuburbId != latest.selectedSuburbId || state.selectedLotId != latest.selectedLotId) {
            layouts.customising = false
            if (state.selectedSuburbId != null || state.selectedLotId != null) side.cardOpened() else side.cardClosed()
        }
        latest = state
        redraw()
        if (state.notice != shownNotice) {
            val text = state.notice
            shownNotice = text
            if (text != null) showNotice(text) else notice.hidden = true
        }
    }.launchIn(scope)

    // One listener for the buttons and one for the rating form, so re-rendering the card never loses them.
    card.addEventListener("click", { event ->
        val target = event.target as? HTMLElement
        when (val action = target?.getAttribute(CardActions.ATTRIBUTE)) {
            CardActions.CLOSE -> viewModel.clear()
            CardActions.RETRY -> viewModel.retry()
            CardActions.REMOVE_RATING -> scope.launch { announce(viewModel.removeRating(), card, ::showNotice) }
            LayoutActions.CUSTOMISE -> { layouts.customising = true; redraw() }
            LayoutActions.DONE -> { layouts.customising = false; redraw() }
            LayoutActions.RESET -> { layouts.reset(latest); redraw() }
            LayoutActions.ROW_UP, LayoutActions.ROW_DOWN, LayoutActions.ROW_TOGGLE -> {
                val label = target.getAttribute(LayoutActions.ROW_ATTRIBUTE)
                if (label != null) { layouts.change(latest, action, label); redraw() }
            }
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

/** The right-hand panel's tabs (ADR 0048), driven by [SidePanelState]. */
private class SidePanel(private val narrow: Boolean) {
    private val root = element("side")
    private val panes = mapOf(SideTab.SUBURB to element("card"), SideTab.FILTERS to element("filters"))
    private val tabs = mapOf(SideTab.SUBURB to element("tab-suburb"), SideTab.FILTERS to element("tab-filters"))
    private var state = SidePanelState.initial(startFolded = narrow)

    init {
        tabs.forEach { (tab, button) -> button.addEventListener("click", { update(state.tapped(tab)) }) }
        update(state)
    }

    private fun update(next: SidePanelState) {
        state = next
        root.setAttribute("data-collapsed", next.collapsed.toString())
        SideTab.entries.forEach { tab ->
            val selected = tab == next.tab
            tabs.getValue(tab).setAttribute("aria-selected", selected.toString())
            tabs.getValue(tab).setAttribute("aria-expanded", (selected && !next.collapsed).toString())
            panes.getValue(tab).hidden = !selected || next.collapsed
        }
    }

    fun cardOpened() = update(state.cardOpened())
    fun cardClosed() = update(state.cardClosed(startFolded = narrow))
    fun setFiltersLabel(text: String) { tabs.getValue(SideTab.FILTERS).textContent = text }
}

private const val NARROW_WINDOW_PX = 600

private fun wireSidePanel(): SidePanel = SidePanel(narrow = window.innerWidth < NARROW_WINDOW_PX)

/**
 * The filter panel (ADR 0035): the values load once, the inputs are drawn once, and moving a slider only recomputes which
 * suburbs pass and restyles the map, with no request.
 */
private fun wireFilters(scope: CoroutineScope, api: FilterApi, view: SuburbMap, onResult: (FilterResult?) -> Unit) {
    val panel = element("filters")
    var data: FilterData? = null

    fun input(id: String) = document.getElementById(id) as? HTMLInputElement

    fun selection(): FilterSelection = FilterSelection(
        FilterCriterion.entries.mapNotNull { criterion ->
            val on = input(FilterIds.on(criterion))?.checked == true
            val low = input(FilterIds.min(criterion))?.value?.toDoubleOrNull()
            val high = input(FilterIds.max(criterion))?.value?.toDoubleOrNull()
            if (on && low != null && high != null) criterion to FilterRange(minOf(low, high), high) else null
        }.toMap(),
    )

    fun scoreSetup(): ScoreSetup = ScoreSetup(
        FilterCriterion.entries.mapNotNull { criterion ->
            val weight = input(ScoreIds.weight(criterion))?.value?.toIntOrNull() ?: return@mapNotNull null
            val preference = (document.getElementById(ScoreIds.preference(criterion)) as? HTMLSelectElement)?.value
                ?.let { name -> Preference.entries.firstOrNull { it.name == name } } ?: return@mapNotNull null
            criterion to ScoreWeight(weight.coerceIn(0, ScoreWeight.MAX_WEIGHT), preference)
        }.toMap(),
    )

    fun refreshScores(loaded: FilterData) {
        val scored = loaded.score(scoreSetup())
        view.showScores(scored.scores)
        document.getElementById(ScoreIds.SUMMARY)?.textContent = scoreSummaryText(scored)
        FilterCriterion.entries.forEach { criterion ->
            input(ScoreIds.weight(criterion))?.let { document.getElementById(ScoreIds.weightOut(criterion))?.textContent = it.value }
        }
        document.getElementById(ScoreIds.RANKING)?.let { list ->
            list.innerHTML = ""
            scoreRankingLines(scored).forEach { line -> list.appendChild(document.createElement("li").also { it.textContent = line }) }
        }
    }

    fun refresh() {
        val loaded = data ?: return
        refreshScores(loaded)
        val result = loaded.apply(selection())
        view.showFilter(result.passingIds)
        onResult(result)
        document.getElementById(FilterIds.SUMMARY)?.textContent = filterSummaryText(result)
        FilterCriterion.entries.forEach { criterion ->
            input(FilterIds.min(criterion))?.let { document.getElementById(FilterIds.minOut(criterion))?.textContent = criterion.format(it.value.toDouble()) }
            input(FilterIds.max(criterion))?.let { document.getElementById(FilterIds.maxOut(criterion))?.textContent = criterion.format(it.value.toDouble()) }
        }
    }

    panel.addEventListener("input", { event ->
        // A criterion's sliders never cross: dragging one past the other pulls the other along.
        val targetId = (event.target as? HTMLElement)?.id
        val criterion = FilterIds.criterionOf(targetId)
        if (criterion != null) {
            // Moving a slider is a request to filter on it, so it switches its criterion on.
            if (targetId != FilterIds.on(criterion)) input(FilterIds.on(criterion))?.checked = true
            val low = input(FilterIds.min(criterion))
            val high = input(FilterIds.max(criterion))
            if (low != null && high != null && low.value.toInt() > high.value.toInt()) {
                if (targetId == FilterIds.min(criterion)) high.value = low.value else low.value = high.value
            }
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

/** Which rows each card shows and in what order (ADR 0039), kept in this browser; [customising] is the edit view and is not remembered. */
private class CardLayouts {
    var suburb: CardLayout = load(SUBURB_LAYOUT_STORAGE)
    var lot: CardLayout = load(LOT_LAYOUT_STORAGE)
    var customising: Boolean = false

    private fun load(key: String): CardLayout = CardLayout.decode(runCatching { localStorage.getItem(key) }.getOrNull())

    private fun save(key: String, layout: CardLayout) {
        runCatching { localStorage.setItem(key, layout.encode()) }
    }

    /** The rows of the card on show, or null when it is not loaded. */
    private fun rows(state: MapUiState): List<CardRow>? {
        val lotCard = state.lotCard
        val suburbCard = state.card
        return when {
            lotCard is LotCardState.Loaded -> LotCardRows.of(lotCard.report)
            lotCard is LotCardState.Hidden && suburbCard is SuburbCardState.Loaded -> SuburbCardRows.of(suburbCard.report)
            else -> null
        }
    }

    private fun showsLot(state: MapUiState) = state.lotCard !is LotCardState.Hidden

    fun change(state: MapUiState, action: String, label: String) {
        val rows = rows(state) ?: return
        val current = if (showsLot(state)) lot else suburb
        val next = when (action) {
            LayoutActions.ROW_UP -> current.moveUp(label, rows)
            LayoutActions.ROW_DOWN -> current.moveDown(label, rows)
            else -> current.toggle(label)
        }
        update(state, next)
    }

    fun reset(state: MapUiState) = update(state, CardLayout())

    private fun update(state: MapUiState, next: CardLayout) {
        if (showsLot(state)) { lot = next; save(LOT_LAYOUT_STORAGE, next) } else { suburb = next; save(SUBURB_LAYOUT_STORAGE, next) }
    }
}

/** Shows the lot card when a lot is selected, else the suburb card; at most one is ever visible. */
private fun renderCard(container: HTMLElement, suburb: SuburbCardState, lot: LotCardState, layouts: CardLayouts) {
    val scrolled = container.scrollTop
    container.innerHTML = ""
    when {
        lot !is LotCardState.Hidden -> container.append { div { lotCard(lot, layouts.lot, layouts.customising) } }
        suburb !is SuburbCardState.Hidden -> container.append { div { suburbCard(suburb, layouts.suburb, layouts.customising) } }
        else -> container.append { p("side-empty") { +SIDE_EMPTY_TEXT } }
    }
    container.scrollTop = scrolled
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
