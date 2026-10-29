package au.homefinder.app.selection

import au.homefinder.app.api.LookupResult
import au.homefinder.app.api.SuburbApi
import au.homefinder.shared.api.SuburbRef
import au.homefinder.shared.model.SuburbReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Suburb Card shows. */
sealed interface SuburbCardState {
    data object Hidden : SuburbCardState

    /** The report is loading; [ref] gives the name straight away when the selection came from a map tap. */
    data class Loading(val suburbId: String, val ref: SuburbRef?) : SuburbCardState
    data class Loaded(val report: SuburbReport) : SuburbCardState
    data class Failed(val suburbId: String, val reason: String) : SuburbCardState
}

/** The whole selection state, held by suburb id so a tap, a search and the shortlist all select the same way. */
data class MapUiState(
    val selectedSuburbId: String? = null,
    val card: SuburbCardState = SuburbCardState.Hidden,
    /** A short message about the last tap that could not be resolved, or null. */
    val notice: String? = null,
)

/** A tap on the map at this zoom. At street level a tap will select a lot (a later PR), so it does not pick a suburb. */
const val SUBURB_SELECT_MAX_ZOOM: Double = 16.0

class MapViewModel(private val api: SuburbApi, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = mutableState.asStateFlow()

    private var job: Job? = null

    /** A tap finds the suburb under it. A tap on no suburb clears the selection; a failed lookup keeps it and says so. */
    fun onMapTap(latitude: Double, longitude: Double, zoom: Double) {
        if (zoom >= SUBURB_SELECT_MAX_ZOOM) return
        launchLatest {
            when (val found = api.suburbAt(latitude, longitude)) {
                is LookupResult.Found -> choose(found.value.suburbId, found.value)
                LookupResult.NotFound -> mutableState.value = MapUiState()
                LookupResult.Unauthorized -> mutableState.update { it.copy(notice = NOTICE_KEY_REJECTED) }
                is LookupResult.Failed -> mutableState.update { it.copy(notice = NOTICE_UNREACHABLE) }
            }
        }
    }

    /** Selects a suburb by id: the entry point for search and the shortlist as well as the map. */
    fun select(suburbId: String) {
        if (suburbId == mutableState.value.selectedSuburbId && mutableState.value.card !is SuburbCardState.Failed) return
        launchLatest { choose(suburbId, null) }
    }

    fun clear() {
        job?.cancel()
        mutableState.value = MapUiState()
    }

    /** Tries the report again after a failure. */
    fun retry() {
        val id = mutableState.value.selectedSuburbId ?: return
        launchLatest { choose(id, (mutableState.value.card as? SuburbCardState.Loading)?.ref) }
    }

    private fun launchLatest(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }

    private suspend fun choose(suburbId: String, ref: SuburbRef?) {
        mutableState.value = MapUiState(selectedSuburbId = suburbId, card = SuburbCardState.Loading(suburbId, ref))
        mutableState.value = when (val result = api.report(suburbId)) {
            is LookupResult.Found -> mutableState.value.copy(card = SuburbCardState.Loaded(result.value))
            LookupResult.NotFound -> MapUiState()
            LookupResult.Unauthorized -> mutableState.value.copy(card = SuburbCardState.Failed(suburbId, REASON_KEY_REJECTED))
            is LookupResult.Failed -> mutableState.value.copy(card = SuburbCardState.Failed(suburbId, result.reason))
        }
    }

    companion object {
        const val NOTICE_KEY_REJECTED: String = "The API key was rejected"
        const val NOTICE_UNREACHABLE: String = "Could not reach the API"
        const val REASON_KEY_REJECTED: String = "the API key was rejected"
    }
}
