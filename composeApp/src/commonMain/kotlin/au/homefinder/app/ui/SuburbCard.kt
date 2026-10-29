package au.homefinder.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import au.homefinder.app.selection.CardRow
import au.homefinder.app.selection.SuburbCardRows
import au.homefinder.app.selection.SuburbCardState
import au.homefinder.shared.model.Confidence

private const val DISCLAIMER = "Indicative only. Verify against the contract of sale, a council planning certificate and a building inspection."

/** What the card says in its header for each state. */
fun cardTitle(card: SuburbCardState): String = when (card) {
    SuburbCardState.Hidden -> ""
    is SuburbCardState.Loading -> card.ref?.name ?: "Loading suburb…"
    is SuburbCardState.Loaded -> card.report.name
    is SuburbCardState.Failed -> "Suburb"
}

fun cardSubtitle(card: SuburbCardState): String? = when (card) {
    is SuburbCardState.Loading -> card.ref?.postcode
    is SuburbCardState.Loaded -> card.report.postcode
    else -> null
}?.let { "Postcode $it" }

fun confidenceLabel(confidence: Confidence): String? = when (confidence) {
    Confidence.HIGH -> "high confidence"
    Confidence.MEDIUM -> "medium confidence"
    Confidence.LOW -> "low confidence"
    Confidence.NONE -> null
}

/** The line under a row: where the value comes from, when, and how far to trust it. */
fun provenanceLine(row: CardRow): String =
    listOfNotNull("Source: ${row.source}", row.asOf?.let { "as of $it" }, confidenceLabel(row.confidence)).joinToString(" · ")

@Composable
fun SuburbCardContent(
    card: SuburbCardState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(cardTitle(card), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                cardSubtitle(card)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            TextButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Close suburb card" }) { Text("Close") }
        }
        when (card) {
            SuburbCardState.Hidden -> Unit
            is SuburbCardState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 16.dp))
            is SuburbCardState.Failed -> {
                Text("Could not load this suburb: ${card.reason}.", modifier = Modifier.padding(vertical = 12.dp))
                Button(onClick = onRetry) { Text("Try again") }
            }
            is SuburbCardState.Loaded -> Column(Modifier.verticalScroll(rememberScrollState())) {
                SuburbCardRows.of(card.report).forEach { row ->
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    CardRowView(row)
                }
                Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun CardRowView(row: CardRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(row.label, style = MaterialTheme.typography.labelMedium)
        if (row.value != null) {
            Text(row.value, style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(
                SuburbCardRows.UNKNOWN,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(provenanceLine(row), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
