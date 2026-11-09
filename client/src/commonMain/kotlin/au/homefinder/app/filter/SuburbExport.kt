package au.homefinder.app.filter

import au.homefinder.app.selection.CardLayout
import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.model.SuburbExportRequest

/** Whether the shaded suburbs can be exported as a PDF now (ADR 0080), and if not, why. */
sealed interface ExportAvailability {
    data class Ready(val count: Int) : ExportAvailability
    data object NoFilter : ExportAvailability
    data object NoMatch : ExportAvailability
    data class TooMany(val count: Int) : ExportAvailability
}

/**
 * The PDF export of the suburbs the selected filters shade (ADR 0080): when it is offered, the words on and around its button, and the request the
 * page sends. Pure, so it is tested without a browser.
 */
object SuburbExport {
    const val BUTTON_TEXT: String = "Export PDF"
    const val READY_HINT: String = "A PDF with every detail of each matching suburb and the filters used."
    const val NO_FILTER_HINT: String = "Switch on a filter to export the matching suburbs as a PDF."
    const val NO_MATCH_HINT: String = "No suburb matches, so there is nothing to export."
    const val UNAUTHORIZED_TEXT: String = "Could not make the PDF: the API key was not accepted."

    fun availability(result: FilterResult?): ExportAvailability {
        val passing = result?.passingIds ?: return ExportAvailability.NoFilter
        return when {
            passing.isEmpty() -> ExportAvailability.NoMatch
            passing.size > ApiContract.MAX_EXPORT_SUBURBS -> ExportAvailability.TooMany(passing.size)
            else -> ExportAvailability.Ready(passing.size)
        }
    }

    /** "Export PDF (12 suburbs)" when ready, otherwise just "Export PDF" (the hint under it says why it is off). */
    fun buttonText(availability: ExportAvailability): String =
        if (availability is ExportAvailability.Ready) "$BUTTON_TEXT (${suburbs(availability.count)})" else BUTTON_TEXT

    fun hintText(availability: ExportAvailability): String = when (availability) {
        is ExportAvailability.Ready -> READY_HINT
        ExportAvailability.NoFilter -> NO_FILTER_HINT
        ExportAvailability.NoMatch -> NO_MATCH_HINT
        is ExportAvailability.TooMany ->
            "${availability.count} suburbs match: narrow the filters to ${ApiContract.MAX_EXPORT_SUBURBS} or fewer to export them as a PDF."
    }

    fun exportingText(count: Int): String = "Making the PDF of ${suburbs(count)}…"

    fun doneText(fileName: String, count: Int): String = "Downloaded $fileName (${suburbs(count)})."

    fun failedText(reason: String): String = "Could not make the PDF: $reason."

    /**
     * The switched-on filters as the panel shows them, one line each in the panel's criterion order: "Homes rented: At most 45%", "Violent crime: No
     * limit". The label is cut before its bracketed explanation, as in the panel's source lines.
     */
    fun filterLines(data: FilterData, selection: FilterSelection): List<String> =
        FilterCriterion.entries.mapNotNull { criterion ->
            val limit = selection.limits[criterion] ?: return@mapNotNull null
            "${criterion.label.substringBefore(" (")}: ${data.limitPrefix(criterion, limit)}${data.limitText(criterion, limit)}"
        }

    /** The request for the shaded suburbs, sorted by name, or null when the export is not offered (see [availability]). */
    fun request(result: FilterResult, data: FilterData, layout: CardLayout): SuburbExportRequest? {
        if (availability(result) !is ExportAvailability.Ready) return null
        val ids = result.passingIds.orEmpty().sortedWith(compareBy({ data.name(it) }, { it }))
        return SuburbExportRequest(ids, filterLines(data, result.selection), layout.encode())
    }

    private fun suburbs(count: Int): String = if (count == 1) "1 suburb" else "$count suburbs"
}
