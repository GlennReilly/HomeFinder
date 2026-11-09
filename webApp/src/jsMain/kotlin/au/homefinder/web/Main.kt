package au.homefinder.web

import au.homefinder.app.api.ApiConfig
import au.homefinder.app.api.FilterApi
import au.homefinder.app.filter.FilterCriterion
import au.homefinder.app.filter.FilterData
import au.homefinder.app.filter.FilterGroups
import au.homefinder.app.filter.FilterSelection
import au.homefinder.app.ui.FILTER_GROUP_CLASS
import au.homefinder.app.ui.FILTER_LOADING_TEXT
import au.homefinder.app.ui.FILTER_UNAVAILABLE_TEXT
import au.homefinder.app.ui.FilterIds
import au.homefinder.app.ui.filterPanel
import au.homefinder.app.ui.filterSummaryText
import au.homefinder.app.api.KtorHttp
import au.homefinder.app.api.LotApi
import au.homefinder.app.api.LookupResult
import au.homefinder.app.api.NaplanApi
import au.homefinder.app.api.RatingApi
import au.homefinder.app.api.SearchApi
import au.homefinder.app.api.SuburbApi
import au.homefinder.app.selection.CardKind
import au.homefinder.app.selection.CardGroups
import au.homefinder.app.selection.CardLayout
import au.homefinder.app.selection.CardScroll
import au.homefinder.app.selection.SchoolsJump
import au.homefinder.app.map.NaplanLabels
import au.homefinder.app.ui.CARD_GROUP_CLASS
import au.homefinder.app.ui.STICKY_TOP_CLASS
import au.homefinder.shared.model.SuburbFiltersResponse
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
import au.homefinder.app.selection.firstTabLabel
import au.homefinder.app.filter.FilterResult
import au.homefinder.app.selection.RatingOutcome
import au.homefinder.app.selection.NaplanOutcome
import au.homefinder.app.selection.SearchViewModel
import au.homefinder.app.selection.SuburbCardState
import au.homefinder.app.ui.Aria
import au.homefinder.app.ui.CardActions
import au.homefinder.app.ui.RatingForm
import au.homefinder.app.ui.NaplanIds
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.dom.append
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.asList
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
    val viewModel = MapViewModel(SuburbApi(config, http), scope, LotApi(config, http), ratingApi, NaplanApi(config, http))
    // A click on a NAPLAN label is wired once the card is (below); until then it does nothing.
    var onNaplanLabel: (String) -> Unit = {}
    val map = SuburbMap(origin, apiKey, viewModel::onMapTap) { suburbId -> onNaplanLabel(suburbId) }

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
    // The filter values also carry each suburb's NAPLAN average, so every load (and the reload after a NAPLAN save or clear) redraws the map's NAPLAN labels (ADR 0076).
    val reloadFilters = wireFilters(
        scope, FilterApi(config, http), map,
        onValues = { values -> map.showNaplan(NaplanLabels.from(values.suburbs)) },
    ) { result -> side.setFiltersLabel(filtersTabLabel(result)) }
    // A saved or cleared NAPLAN figure changes the NAPLAN filter's values (ADR 0074), so the panel reloads them, keeping what is switched on.
    viewModel.naplanVersion.drop(1).onEach { reloadFilters() }.launchIn(scope)

    // The star scores under suburb names: loaded at the start and again whenever one is saved or removed.
    suspend fun refreshRatings() {
        (ratingApi.all() as? LookupResult.Found)?.let { map.showRatings(it.value.ratings) }
    }
    viewModel.ratingsVersion.onEach { refreshRatings() }.launchIn(scope)

    val layouts = CardLayouts()
    var latest = MapUiState()
    val scrollMemory = CardScrollMemory()
    fun redraw() = renderCard(card, latest.card, latest.lotCard, layouts, scrollMemory)

    // A click on a suburb's NAPLAN label, or the card's "Jump to Schools" button (ADR 0076): show the suburb's card, open its Schools group and scroll
    // it in under the pinned header, once the card has loaded.
    var jump = SchoolsJump()
    fun scrollToSchools() {
        if (!layouts.customising) layouts.groupOpen.putAll(SchoolsJump.opened(layouts.groupOpen))
        redraw()
        val group = card.querySelector("details.$CARD_GROUP_CLASS[${LayoutActions.GROUP_ATTRIBUTE}='${SchoolsJump.GROUP_TITLE}']") as? HTMLElement ?: return
        val sticky = card.querySelector(".$STICKY_TOP_CLASS") as? HTMLElement
        val offset = group.getBoundingClientRect().top - card.getBoundingClientRect().top
        card.scrollTop = SchoolsJump.scrollTop(card.scrollTop.toInt(), offset.toInt(), sticky?.getBoundingClientRect()?.height?.toInt() ?: 0).toDouble()
        scrollMemory.value = scrollMemory.value.copy(remembered = card.scrollTop.toInt())
        // Keyboard users land on the group's heading; focusing must not scroll again.
        group.querySelector("summary")?.asDynamic()?.focus(jsObject("preventScroll" to true))
    }
    fun jumpToSchools(suburbId: String) {
        side.cardOpened()
        viewModel.select(suburbId)
        val decision = jump.requested(suburbId, latest)
        jump = decision.next
        if (decision.go) scrollToSchools()
    }
    onNaplanLabel = ::jumpToSchools

    viewModel.state.onEach { state ->
        map.showSelection(state.selectedSuburbId, state.selectedLotId)
        if (state.selectedSuburbId != latest.selectedSuburbId || state.selectedLotId != latest.selectedLotId) {
            layouts.customising = false
            layouts.naplanOpen = null
            if (state.selectedSuburbId != null || state.selectedLotId != null) side.cardOpened() else side.cardClosed()
        }
        latest = state
        side.setFirstTabLabel(firstTabLabel(lotShown = state.lotCard !is LotCardState.Hidden))
        redraw()
        val decision = jump.onState(state)
        jump = decision.next
        if (decision.go) scrollToSchools()
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
            CardActions.JUMP_SCHOOLS -> latest.selectedSuburbId?.let(::jumpToSchools)
            CardActions.SHOW_UNKNOWN -> { layouts.showUnknown = true; redraw() }
            CardActions.HIDE_UNKNOWN -> { layouts.showUnknown = false; redraw() }
            CardActions.REMOVE_RATING -> scope.launch { announce(viewModel.removeRating(), card, ::showNotice) }
            // One NAPLAN form at a time, under the school whose link was pressed (ADR 0068); pressing the open one's link again closes it.
            CardActions.EDIT_NAPLAN -> {
                layouts.naplanOpen = target.id.takeIf { it != layouts.naplanOpen }
                redraw()
                (document.getElementById(NaplanIds.SCORE) as? HTMLInputElement)?.focus()
            }
            CardActions.CANCEL_NAPLAN -> { layouts.naplanOpen = null; redraw() }
            CardActions.CLEAR_NAPLAN -> naplanCode(target)?.let { code ->
                scope.launch { announceNaplan(viewModel.removeNaplan(code), card, layouts, ::redraw, ::showNotice) }
            }
            LayoutActions.CUSTOMISE -> { layouts.customising = true; redraw() }
            LayoutActions.DONE -> { layouts.customising = false; redraw() }
            LayoutActions.RESET -> { layouts.reset(latest); redraw() }
            LayoutActions.ROW_UP, LayoutActions.ROW_DOWN, LayoutActions.ROW_TOGGLE -> {
                val label = target.getAttribute(LayoutActions.ROW_ATTRIBUTE)
                if (label != null) { layouts.change(latest, action, label); redraw() }
            }
            LayoutActions.GROUP_UP, LayoutActions.GROUP_DOWN -> {
                val title = target.getAttribute(LayoutActions.GROUP_ATTRIBUTE)
                if (title != null) { layouts.change(latest, action, title); redraw() }
            }
        }
    })
    // A group folding open or shut is remembered for this page load, so a redraw (another suburb, a saved rating) keeps it as you left it (ADR 0064).
    // `toggle` does not bubble, hence the capturing listener.
    card.addEventListener("toggle", { event ->
        val details = event.target as? HTMLElement
        val title = details?.getAttribute(LayoutActions.GROUP_ATTRIBUTE)
        if (details != null && title != null && !layouts.customising) layouts.groupOpen[title] = details.hasAttribute("open")
    }, true)
    card.addEventListener("submit", { event ->
        val form = event.target as? HTMLFormElement
        if (form?.hasAttribute(RatingForm.ATTRIBUTE) == true) {
            event.preventDefault()
            val stars = (form.elements.namedItem(RatingForm.STARS_FIELD) as? HTMLInputElement)?.value.orEmpty()
            val reviews = (form.elements.namedItem(RatingForm.REVIEWS_FIELD) as? HTMLInputElement)?.value.orEmpty()
            scope.launch { announce(viewModel.saveRating(stars, reviews), card, ::showNotice) }
        } else if (form?.id == NaplanIds.FORM) {
            event.preventDefault()
            val code = naplanCode(form) ?: return@addEventListener
            fun field(name: String) = (form.elements.namedItem(name) as? HTMLInputElement)?.value.orEmpty()
            scope.launch {
                announceNaplan(viewModel.saveNaplan(code, field(NaplanIds.SCORE)), card, layouts, ::redraw, ::showNotice)
            }
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
            tabs.getValue(tab).setAttribute(Aria.SELECTED, selected.toString())
            tabs.getValue(tab).setAttribute(Aria.EXPANDED, (selected && !next.collapsed).toString())
            panes.getValue(tab).hidden = !selected || next.collapsed
        }
    }

    fun cardOpened() = update(state.cardOpened())
    fun cardClosed() = update(state.cardClosed(startFolded = narrow))
    fun setFirstTabLabel(text: String) { tabs.getValue(SideTab.SUBURB).textContent = text }
    fun setFiltersLabel(text: String) { tabs.getValue(SideTab.FILTERS).textContent = text }
}

private const val NARROW_WINDOW_PX = 600

private fun wireSidePanel(): SidePanel = SidePanel(narrow = window.innerWidth < NARROW_WINDOW_PX)

/**
 * The filter panel (ADR 0035): the values load once, the inputs are drawn once, and moving a slider (one per criterion, ADR 0052) only recomputes which
 * suburbs pass and restyles the map, with no request.
 */
private fun wireFilters(
    scope: CoroutineScope,
    api: FilterApi,
    view: SuburbMap,
    onValues: (SuburbFiltersResponse) -> Unit,
    onResult: (FilterResult?) -> Unit,
): () -> Unit {
    val panel = element("filters")
    var data: FilterData? = null
    var loading: Job? = null

    fun input(id: String) = document.getElementById(id) as? HTMLInputElement

    fun selection(): FilterSelection = FilterSelection(
        FilterCriterion.entries.mapNotNull { criterion ->
            val on = input(FilterIds.on(criterion))?.checked == true
            val limit = input(FilterIds.limit(criterion))?.value?.toDoubleOrNull()
            if (on && limit != null) criterion to limit else null
        }.toMap(),
    )

    fun refresh() {
        val loaded = data ?: return
        val result = loaded.apply(selection())
        view.showFilter(result.passingIds)
        onResult(result)
        (document.getElementById(FilterIds.RESET) as? HTMLButtonElement)?.disabled = !result.selection.isActive
        document.getElementById(FilterIds.SUMMARY)?.textContent = filterSummaryText(result)
        // The "N on" beside each group title (ADR 0066).
        val on = result.selection.limits.keys
        FilterGroups.arrange(on).forEach { group -> document.getElementById(group.countId)?.textContent = FilterGroups.onText(group, on) }
        FilterCriterion.entries.forEach { criterion ->
            input(FilterIds.limit(criterion))?.let {
                val limit = it.value.toDouble()
                document.getElementById(FilterIds.limitPrefix(criterion))?.textContent = loaded.limitPrefix(criterion, limit)
                document.getElementById(FilterIds.limitOut(criterion))?.textContent = loaded.limitText(criterion, limit)
            }
        }
    }

    // Reset all: every filter off, every slider back to the value it was drawn with (its starting end).
    panel.addEventListener("click", { event ->
        if ((event.target as? HTMLElement)?.id == FilterIds.RESET) {
            FilterCriterion.entries.forEach { criterion ->
                input(FilterIds.on(criterion))?.checked = false
                input(FilterIds.limit(criterion))?.let { it.value = it.defaultValue }
            }
            refresh()
        }
    })
    panel.addEventListener("input", { event ->
        val targetId = (event.target as? HTMLElement)?.id
        val criterion = FilterIds.criterionOf(targetId)
        if (criterion != null) {
            // Moving a slider is a request to filter on it, so it switches its criterion on.
            if (targetId != FilterIds.on(criterion)) input(FilterIds.on(criterion))?.checked = true
        }
        refresh()
    })

    // Draws the panel from fresh values. A reload (data already shown) keeps the switched-on filters, their limits and the open groups; a failed reload
    // leaves the panel as it was.
    fun load() {
        loading?.cancel()
        val first = data == null
        if (first) panel.textContent = FILTER_LOADING_TEXT
        loading = scope.launch {
            when (val result = api.values()) {
                is LookupResult.Found -> {
                    onValues(result.value)
                    val loaded = FilterData.from(result.value)
                    val kept = if (first) FilterSelection() else selection().carriedTo(loaded)
                    val openGroups = panel.querySelectorAll("details.$FILTER_GROUP_CLASS[open]").asList().mapNotNull { (it as? HTMLElement)?.id }
                    data = loaded
                    panel.innerHTML = ""
                    panel.append { div { filterPanel(loaded, loaded.apply(kept)) } }
                    if (!first) {
                        panel.querySelectorAll("details.$FILTER_GROUP_CLASS").asList().forEach { node ->
                            val group = node as? HTMLElement ?: return@forEach
                            if (group.id in openGroups) group.setAttribute(HTML_OPEN, HTML_OPEN) else group.removeAttribute(HTML_OPEN)
                        }
                    }
                    kept.limits.forEach { (criterion, limit) ->
                        input(FilterIds.on(criterion))?.checked = true
                        input(FilterIds.limit(criterion))?.value = limit.toLong().toString()
                    }
                    if (!first) refresh()
                }
                else -> if (first) panel.textContent = FILTER_UNAVAILABLE_TEXT
            }
        }
    }

    load()
    return ::load
}

/** The attribute (and its value) that shows a `details` open. */
private const val HTML_OPEN: String = "open"

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
        input.setAttribute(Aria.EXPANDED, searchIsOpen.toString())
        if (state.highlighted >= 0 && searchIsOpen) input.setAttribute(Aria.ACTIVE_DESCENDANT, SearchIds.option(state.highlighted))
        else input.removeAttribute(Aria.ACTIVE_DESCENDANT)
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

    /** Which groups are open (ADR 0064), by title; a group not listed starts as its default says. Not remembered across page loads. */
    val groupOpen: MutableMap<String, Boolean> = mutableMapOf()

    /** Whether rows without data are drawn (ADR 0058); not remembered, so each page load starts with them left off. */
    var showUnknown: Boolean = false

    /** The id of the NAPLAN edit link whose form is open (ADR 0068), or null; closed again when the selection changes. */
    var naplanOpen: String? = null

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
        val groups = if (showsLot(state)) CardGroups.LOT else CardGroups.SUBURB
        val next = when (action) {
            LayoutActions.ROW_UP -> current.moveRowInGroup(label, rows, groups, -1)
            LayoutActions.ROW_DOWN -> current.moveRowInGroup(label, rows, groups, 1)
            LayoutActions.GROUP_UP -> current.moveGroup(label, rows, groups, -1)
            LayoutActions.GROUP_DOWN -> current.moveGroup(label, rows, groups, 1)
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
private fun renderCard(container: HTMLElement, suburb: SuburbCardState, lot: LotCardState, layouts: CardLayouts, scroll: CardScrollMemory) {
    val kind = when {
        lot !is LotCardState.Hidden -> CardKind.LOT
        suburb !is SuburbCardState.Hidden -> CardKind.SUBURB
        else -> CardKind.NONE
    }
    val loaded = lot is LotCardState.Loaded || (lot is LotCardState.Hidden && suburb is SuburbCardState.Loaded)
    // The position is read before the card is replaced and put back after, so choosing another suburb keeps the same row in view (ADR 0053).
    val plan = scroll.value.plan(kind, loaded, container.scrollTop.toInt())
    scroll.value = plan.memory
    container.innerHTML = ""
    when (kind) {
        CardKind.LOT -> container.append { div { lotCard(lot, layouts.lot, layouts.customising, layouts.showUnknown, layouts.groupOpen, layouts.naplanOpen) } }
        CardKind.SUBURB -> container.append { div { suburbCard(suburb, layouts.suburb, layouts.customising, layouts.showUnknown, layouts.groupOpen, layouts.naplanOpen) } }
        CardKind.NONE -> container.append { p("side-empty") { +SIDE_EMPTY_TEXT } }
    }
    container.scrollTop = plan.restoreTo.toDouble()
}

/** The card's scroll memory, kept between redraws. */
private class CardScrollMemory(var value: CardScroll = CardScroll())

/** Says how saving or removing a score went: success as a notice (the card has just been redrawn), a problem beside the form. */
private fun announce(outcome: RatingOutcome, card: HTMLElement, showNotice: (String) -> Unit) {
    when (outcome) {
        RatingOutcome.Saved -> showNotice("Homely rating saved")
        RatingOutcome.Removed -> showNotice("Homely rating removed")
        is RatingOutcome.Rejected -> card.querySelector(".${RatingForm.MESSAGE_CLASS}")?.textContent = outcome.message
        is RatingOutcome.Failed -> card.querySelector(".${RatingForm.MESSAGE_CLASS}")?.textContent = "Could not save: ${outcome.message}"
    }
}

/** The school code a NAPLAN control or form carries, from the element itself or the form it sits in. */
private fun naplanCode(element: HTMLElement): String? =
    (element.closest("[${NaplanIds.CODE_ATTRIBUTE}]") as? HTMLElement)?.getAttribute(NaplanIds.CODE_ATTRIBUTE)

/** Says how saving or clearing a NAPLAN figure went: success closes the form and shows a notice, a problem shows beside the form. */
private fun announceNaplan(outcome: NaplanOutcome, card: HTMLElement, layouts: CardLayouts, redraw: () -> Unit, showNotice: (String) -> Unit) {
    when (outcome) {
        NaplanOutcome.Saved, NaplanOutcome.Removed -> {
            layouts.naplanOpen = null
            redraw()
            showNotice(if (outcome == NaplanOutcome.Saved) "NAPLAN score saved" else "NAPLAN score cleared")
        }
        is NaplanOutcome.Rejected -> card.querySelector("#${NaplanIds.MESSAGE}")?.textContent = outcome.message
        is NaplanOutcome.Failed -> card.querySelector("#${NaplanIds.MESSAGE}")?.textContent = "Could not save: ${outcome.message}"
    }
}
