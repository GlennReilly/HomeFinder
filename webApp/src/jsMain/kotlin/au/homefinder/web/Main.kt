package au.homefinder.web

import au.homefinder.app.api.ApiConfig
import au.homefinder.app.api.KtorHttpGet
import au.homefinder.app.api.SuburbApi
import au.homefinder.app.selection.MapUiState
import au.homefinder.app.selection.MapViewModel
import au.homefinder.app.selection.SuburbCardState
import au.homefinder.app.ui.CardActions
import au.homefinder.app.ui.suburbCard
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
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
    val viewModel = MapViewModel(SuburbApi(ApiConfig(origin, apiKey), KtorHttpGet()), scope)
    val map = SuburbMap(origin, apiKey, viewModel::onMapTap)

    val card = element("card")
    val notice = element("notice")
    var shownNotice: String? = null

    viewModel.state.onEach { state ->
        map.showSelection(state.selectedSuburbId)
        renderCard(card, state.card)
        if (state.notice != shownNotice) {
            shownNotice = state.notice
            notice.textContent = state.notice.orEmpty()
            notice.hidden = state.notice == null
            if (state.notice != null) window.setTimeout({ notice.hidden = true }, NOTICE_MILLIS)
        }
    }.launchIn(scope)

    // One listener for both buttons, so re-rendering the card never loses them.
    card.addEventListener("click", { event ->
        when ((event.target as? HTMLElement)?.getAttribute(CardActions.ATTRIBUTE)) {
            CardActions.CLOSE -> viewModel.clear()
            CardActions.RETRY -> viewModel.retry()
        }
    })
    document.addEventListener("keydown", { event ->
        if ((event as KeyboardEvent).key == ESCAPE) viewModel.clear()
    })
}

private fun renderCard(container: HTMLElement, state: SuburbCardState) {
    container.hidden = state is SuburbCardState.Hidden
    container.innerHTML = ""
    if (state !is SuburbCardState.Hidden) container.append { div { suburbCard(state) } }
}
