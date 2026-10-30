package au.homefinder.app.selection

import au.homefinder.shared.model.LotReport

/** The rows of the basic lot card (ADR 0022): the measured block size, then every other factor in the spec's order. */
object LotCardRows {
    /**
     * Only the block size has a value yet. The other factors are formatted with a plain fallback; each gets its own
     * wording when its pipeline lands and a real value can reach the card.
     */
    fun of(report: LotReport): List<CardRow> = listOf(
        SuburbCardRows.row("Block size", report.blockAreaM2, Formats::squareMetres),
        SuburbCardRows.row("Wall material", report.wallMaterial) { it.toString() },
        SuburbCardRows.row("Backyard aspect", report.backyardAspect) { it.toString() },
        SuburbCardRows.row("Flood", report.flood) { it.toString() },
        SuburbCardRows.row("Slope", report.slope) { it.toString() },
        SuburbCardRows.row("Estimated price", report.estimatedPriceAud) { it.toString() },
        SuburbCardRows.row("Distance to the beach", report.beach) { it.toString() },
        SuburbCardRows.row("Schools", report.schools) { it.toString() },
        SuburbCardRows.row("Parks", report.parks) { it.toString() },
        SuburbCardRows.row("Shopping", report.shopping) { it.toString() },
        SuburbCardRows.row("Transport", report.transport) { it.toString() },
    )
}
