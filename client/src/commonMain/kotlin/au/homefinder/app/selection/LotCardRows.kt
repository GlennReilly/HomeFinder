package au.homefinder.app.selection

import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.ParkAccess
import au.homefinder.shared.model.Proximity
import au.homefinder.shared.model.ProximityList

/** The rows of the basic lot card (ADR 0022): the measured block size, then every other factor in the spec's order. */
object LotCardRows {
    /**
     * The block size and the OpenStreetMap proximity factors (ADR 0023) have values. The remaining factors are
     * formatted with a plain fallback; each gets its own wording when its pipeline lands and a real value can reach the card.
     */
    fun of(report: LotReport): List<CardRow> = listOf(
        SuburbCardRows.row("Block size", report.blockAreaM2, Formats::squareMetres),
        SuburbCardRows.row("Wall material", report.wallMaterial) { it.toString() },
        SuburbCardRows.row("Backyard aspect", report.backyardAspect) { it.toString() },
        SuburbCardRows.row("Flood", report.flood) { it.toString() },
        SuburbCardRows.row("Slope", report.slope) { it.toString() },
        SuburbCardRows.row("Estimated price", report.estimatedPriceAud) { it.toString() },
        SuburbCardRows.row("Distance to the beach", report.beach, ::beach),
        SuburbCardRows.row("Schools", report.schools) { it.toString() },
        SuburbCardRows.row("Parks", report.parks, ::parks),
        SuburbCardRows.row("Shopping", report.shopping, ::shopping),
        SuburbCardRows.row("Transport", report.transport) { it.toString() },
    )

    /** A walking estimate is only worth showing for a walk: beyond this many minutes the distance alone is said. */
    const val WALK_ESTIMATE_MAX_MINUTES: Int = 60

    private fun walk(place: Proximity): String =
        place.walkingMinutes?.takeIf { it <= WALK_ESTIMATE_MAX_MINUTES }?.let { ", about $it min walk (estimated)" }.orEmpty()

    private fun away(place: Proximity): String = "${Formats.distance(place.straightLineM)} away" + walk(place)

    private fun beach(place: Proximity): String = "${place.name ?: "Nearest beach"}, ${away(place)}"

    private fun parks(access: ParkAccess): String {
        val nearest = access.nearest.joinToString("; ") { "${it.name ?: "Unnamed park"}, ${away(it)}" }
        val area = access.areaWithin800mM2?.let { "; ${Formats.squareMetres(it)} of park within 800 m" }.orEmpty()
        return nearest + area
    }

    private fun shopping(places: ProximityList): String = places.items.joinToString("; ") {
        "${it.category?.replaceFirstChar { c -> c.uppercase() } ?: "Shop"}: ${it.name ?: "unnamed"}, ${away(it)}"
    }
}
