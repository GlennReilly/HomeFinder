package au.homefinder.server

import au.homefinder.app.selection.CardGroups
import au.homefinder.app.selection.CardLayout
import au.homefinder.app.selection.CardRow
import au.homefinder.app.selection.SuburbCardRows
import au.homefinder.app.ui.CARD_DISCLAIMER
import au.homefinder.app.ui.provenanceLine
import au.homefinder.shared.model.NaplanRules
import au.homefinder.shared.model.SuburbReport

/** One row of a suburb in the PDF: its label, its value (null for the Schools row, whose schools are listed one per line), its note (the card's "Details" text, or the reason a row has no data) and its full source line. */
data class ExportLine(val label: String, val value: String?, val subLines: List<String> = emptyList(), val source: String? = null, val note: String? = null)

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
 * PDF says what the card says (ADR 0080). Every row is listed, with its note and whole source line, and a row with no data says "No data" and why.
 * Rows you hid with Customise are left out, as on the card.
 */
object SuburbExportContent {
    /** The title of the last group, which holds the Homely score you entered (on the card it sits in its own block at the end, ADR 0060). */
    /** The value of a row that has none. */
    const val NO_DATA: String = "No data"

    const val HOMELY_TITLE: String = "Homely"

    fun suburb(report: SuburbReport, layout: CardLayout): ExportSuburb {
        val groups = layout.arrangeGroups(SuburbCardRows.of(report), CardGroups.SUBURB).mapNotNull { group ->
            val lines = group.rows.filterNot { it.hidden }.map { line(it.row, report) }
            lines.takeIf { it.isNotEmpty() }?.let { ExportGroup(group.title, it) }
        }
        val homely = SuburbCardRows.homely(report)?.let { ExportGroup(HOMELY_TITLE, listOf(line(it, report))) }
        return ExportSuburb(report.name, report.postcode, groups + listOfNotNull(homely))
    }

    private fun line(row: CardRow, report: SuburbReport): ExportLine {
        val source = provenanceLine(row).takeIf { it.isNotEmpty() }
        val note = row.note?.trim()?.takeIf { it.isNotEmpty() }
        val value = row.value ?: NO_DATA
        if (row.label != SuburbCardRows.SCHOOLS_LABEL) return ExportLine(row.label, value, source = source, note = note)
        val zones = report.zonedSchools.value?.zones.orEmpty()
        if (zones.isEmpty()) return ExportLine(row.label, value, source = source, note = note)
        // As on the card: each zoned school with its kind, years and share, then its ICSEA and your NAPLAN figure on lines of their own (ADR 0071, 0073).
        val subLines = zones.flatMap { zone ->
            listOfNotNull(
                "${zone.school.name}: ${SuburbCardRows.zoneDetail(zone)}",
                SuburbCardRows.icseaText(zone.school)?.let { "  $it" },
                zone.school.naplan?.let { "  ${NaplanRules.label(it)}" },
            )
        }
        return ExportLine(row.label, null, subLines, source, note)
    }
}
