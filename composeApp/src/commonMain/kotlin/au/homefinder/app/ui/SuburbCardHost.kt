package au.homefinder.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import au.homefinder.app.selection.CardLayout
import au.homefinder.app.selection.SIDE_PANEL_WIDTH_DP
import au.homefinder.app.selection.SuburbCardState
import au.homefinder.app.selection.cardLayoutFor

private val SHEET_PEEK_HEIGHT = 200.dp
private val PANEL_ELEVATION = 8.dp

/**
 * Hosts the map and the Suburb Card: a bottom sheet on narrow windows and a side panel on wide ones (ADR 0017). The
 * card opens when a suburb is selected; closing it, swiping the sheet away, Back or Escape clears the selection.
 */
@Composable
fun SuburbCardHost(
    card: SuburbCardState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        when (cardLayoutFor(maxWidth.value)) {
            CardLayout.BOTTOM_SHEET -> BottomSheetLayout(card, onClose, onRetry, content)
            CardLayout.SIDE_PANEL -> SidePanelLayout(card, onClose, onRetry, content)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetLayout(card: SuburbCardState, onClose: () -> Unit, onRetry: () -> Unit, content: @Composable () -> Unit) {
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Hidden, skipHiddenState = false)
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
    val visible = card !is SuburbCardState.Hidden
    var shown by remember { mutableStateOf(false) }
    var lastCard by remember { mutableStateOf(card) }
    if (visible) lastCard = card

    LaunchedEffect(visible) {
        if (visible) {
            sheetState.partialExpand()
            shown = true
        } else {
            shown = false
            sheetState.hide()
        }
    }
    // Swiping the sheet away clears the selection (once it has actually been shown).
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.currentValue }.collect { if (it == SheetValue.Hidden && shown) onClose() }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = SHEET_PEEK_HEIGHT,
        sheetContent = { SuburbCardContent(lastCard, onClose, onRetry) },
    ) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun SidePanelLayout(card: SuburbCardState, onClose: () -> Unit, onRetry: () -> Unit, content: @Composable () -> Unit) {
    var lastCard by remember { mutableStateOf(card) }
    if (card !is SuburbCardState.Hidden) lastCard = card
    Box(Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = card !is SuburbCardState.Hidden,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            Surface(Modifier.fillMaxHeight().width(SIDE_PANEL_WIDTH_DP.dp), shadowElevation = PANEL_ELEVATION) {
                SuburbCardContent(lastCard, onClose, onRetry)
            }
        }
    }
}
