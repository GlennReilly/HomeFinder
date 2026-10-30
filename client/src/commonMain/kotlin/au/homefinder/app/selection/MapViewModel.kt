package au.homefinder.app.selection

import au.homefinder.app.api.LookupResult
import au.homefinder.app.api.LotApi
import au.homefinder.app.api.SuburbApi
import au.homefinder.shared.api.SuburbRef
import au.homefinder.shared.model.LotRef
import au.homefinder.shared.model.LotReport
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

/** What the lot card shows (ADR 0022). */
sealed interface LotCardState {
    data object Hidden : LotCardState
    data class Loading(val lotId: String, val ref: LotRef?) : LotCardState
    data class Loaded(val report: LotReport) : LotCardState
    data class Failed(val lotId: String, val reason: String) : LotCardState
}

/**
 * The whole selection state: a suburb (held by id so a tap, a search and the shortlist all select the same way) or a
 * lot, never both. At street level a tap selects a lot, at wider zoom a suburb.
 */
data class MapUiState(
    val selectedSuburbId: String? = null,
    val card: SuburbCardState = SuburbCardState.Hidden,
    val selectedLotId: String? = null,
    val lotCard: LotCardState = LotCardState.Hidden,
    /** A short message about the last tap that could not be resolved, or null. */
    val notice: String? = null,
)

/** From this zoom a tap selects a lot, below it a suburb (spec zoom table; lot tiles start at the same zoom). */
const val LOT_SELECT_MIN_ZOOM: Double = 16.0

class MapViewModel(private val api: SuburbApi, private val scope: CoroutineScope, private val lots: LotApi? = null) {
    private val mutableState = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = mutableState.asStateFlow()

    private var job: Job? = null

    /** A tap finds the suburb under it. A tap on no suburb clears the selection; a failed lookup keeps it and says so. */
    fun onMapTap(latitude: Double, longitude: Double, zoom: Double) {
        if (zoom >= LOT_SELECT_MIN_ZOOM) {
            lots?.let { lotApi -> launchLatest { tapLot(lotApi, latitude, longitude) } }
            return
        }
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
        mutableState.value.selectedLotId?.let { lotId -> lots?.let { api -> launchLatest { chooseLot(api, lotId, null) } }; return }
        val id = mutableState.value.selectedSuburbId ?: return
        launchLatest { choose(id, (mutableState.value.card as? SuburbCardState.Loading)?.ref) }
    }

    private fun launchLatest(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }

    private suspend fun tapLot(lotApi: LotApi, latitude: Double, longitude: Double) {
        when (val found = lotApi.lotAt(latitude, longitude)) {
            is LookupResult.Found -> chooseLot(lotApi, found.value.lotId, found.value)
            LookupResult.NotFound -> mutableState.value = MapUiState()
            LookupResult.Unauthorized -> mutableState.update { it.copy(notice = NOTICE_KEY_REJECTED) }
            is LookupResult.Failed -> mutableState.update { it.copy(notice = NOTICE_UNREACHABLE) }
        }
    }

    private suspend fun chooseLot(lotApi: LotApi, lotId: String, ref: LotRef?) {
        mutableState.value = MapUiState(selectedLotId = lotId, lotCard = LotCardState.Loading(lotId, ref))
        mutableState.value = when (val result = lotApi.report(lotId)) {
            is LookupResult.Found -> mutableState.value.copy(lotCard = LotCardState.Loaded(result.value))
            LookupResult.NotFound -> MapUiState()
            LookupResult.Unauthorized -> mutableState.value.copy(lotCard = LotCardState.Failed(lotId, REASON_KEY_REJECTED))
            is LookupResult.Failed -> mutableState.value.copy(lotCard = LotCardState.Failed(lotId, result.reason))
        }
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
