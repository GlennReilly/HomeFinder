package au.homefinder.server

import au.homefinder.app.selection.CardGroups
import au.homefinder.app.selection.CardLayout
import au.homefinder.app.selection.CardRow
import au.homefinder.app.selection.SuburbCardRows
import au.homefinder.app.ui.CARD_DISCLAIMER
import au.homefinder.app.ui.confidenceLabel
import au.homefinder.app.ui.provenanceLine
import au.homefinder.shared.model.NaplanRules
import au.homefinder.shared.model.NaplanScore
import au.homefinder.shared.model.SuburbReport

/** One row of a suburb in the PDF: its label, its value (null for the Schools row, whose schools are listed one per line), and a short source line. */
data class ExportLine(val label: String, val value: String?, val subLines: List<String> = emptyList(), val source: String? = null)

/** One card group's rows. */
data class ExportGroup(val title: String, val lines: List<ExportLine>)

/** One suburb's section of the PDF. */
data class ExportSuburb(val name: String, val postcode: String?, val groups: List<ExportGroup>)

/** Everything the PDF shows (ADR 0080): the date it was made, the filters used, one section per suburb, and how many asked-for suburbs were not found. */
data class ExportDocument(
    val date: String,
    val filters: List<String>,
    val suburbs: List<ExportSuburb>,
    val notFound: Int = 0,
) {
    val disclaimer: String get() = CARD_DISCLAIMER
}

/**
 * Turns suburb reports into the PDF's sections with the Suburb Card's own row building ([SuburbCardRows], [CardGroups]) and your card layout, so the
 * PDF says what the card says (ADR 0080). Rows with no value are left out; rows you hid on the card are kept, because the export is the full details.
 */
object SuburbExportContent {
    /** A source line longer than this is cut to its confidence, so a row's provenance does not fill the page. */
    const val MAX_SOURCE_CHARS: Int = 160

    /** The title of the last group, which holds the Homely score you entered (on the card it sits in its own block at the end, ADR 0060). */
    const val HOMELY_TITLE: String = "Homely"

    /** Added to a school's NAPLAN line: the figure is one you typed in (ADR 0068, 0069). */
    val NAPLAN_OWN: String = " (${NaplanScore.SOURCE.removeSuffix(".").lowercase()})"

    fun suburb(report: SuburbReport, layout: CardLayout): ExportSuburb {
        val groups = layout.arrangeGroups(SuburbCardRows.of(report), CardGroups.SUBURB).mapNotNull { group ->
            val lines = group.rows.map { it.row }.filterNot { it.isUnknown }.map { line(it, report) }
            lines.takeIf { it.isNotEmpty() }?.let { ExportGroup(group.title, it) }
        }
        val homely = SuburbCardRows.homely(report)?.let { ExportGroup(HOMELY_TITLE, listOf(line(it, report))) }
        return ExportSuburb(report.name, report.postcode, groups + listOfNotNull(homely))
    }

    private fun line(row: CardRow, report: SuburbReport): ExportLine {
        val source = provenanceLine(row).takeIf { it.isNotEmpty() }?.let { if (it.length <= MAX_SOURCE_CHARS) it else confidenceLabel(row.confidence) }
        if (row.label != SuburbCardRows.SCHOOLS_LABEL) return ExportLine(row.label, row.value, source = source)
        val zones = report.zonedSchools.value?.zones.orEmpty()
        if (zones.isEmpty()) return ExportLine(row.label, row.value, source = source)
        // As on the card: each zoned school with its kind, years and share, then its ICSEA and your NAPLAN figure on lines of their own (ADR 0071, 0073).
        val subLines = zones.flatMap { zone ->
            listOfNotNull(
                "${zone.school.name}: ${SuburbCardRows.zoneDetail(zone)}",
                SuburbCardRows.icseaText(zone.school)?.let { "  $it" },
                zone.school.naplan?.let { "  ${NaplanRules.label(it)}$NAPLAN_OWN" },
            )
        }
        return ExportLine(row.label, null, subLines, source)
    }
}
