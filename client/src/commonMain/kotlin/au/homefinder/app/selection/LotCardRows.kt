package au.homefinder.app.selection

import au.homefinder.shared.model.DrainageHint
import au.homefinder.shared.model.DrainageProximity
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.ParkAccess
import au.homefinder.shared.model.Proximity
import au.homefinder.shared.model.ProximityList
import au.homefinder.shared.model.SlopeSummary
import kotlin.math.roundToLong

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
        SuburbCardRows.row("Slope", report.slope, ::slope),
        SuburbCardRows.row("Height above sea level", report.elevationM, ::height),
        SuburbCardRows.row("Height above drainage", report.heightAboveDrainage, ::drainage),
        SuburbCardRows.row("Estimated price", report.estimatedPriceAud) { it.toString() },
        SuburbCardRows.row("Distance to the beach", report.beach, ::beach),
        SuburbCardRows.row("Schools", report.schools) { it.toString() },
        SuburbCardRows.row("Parks", report.parks, ::parks),
        SuburbCardRows.row("Shopping", report.shopping, ::shopping),
        SuburbCardRows.row("Transport", report.transport) { it.toString() },
    )

    fun height(metres: Double): String = "${Formats.decimal(metres, 1)} m"

    /** "Moderate, 12.0% on average (steepest 21.3%)": the class from the mean, then the figures it came from. */
    fun slope(slope: SlopeSummary): String =
        "${slope.slopeClass.name.lowercase().replaceFirstChar { it.uppercase() }}, " +
            "${Formats.decimal(slope.meanPercent, 1)}% on average (steepest ${Formats.decimal(slope.maxPercent, 1)}%)"

    /** Streams draining fewer hectares than this show one decimal place, bigger ones whole hectares. */
    private const val HECTARE_DECIMALS_BELOW: Double = 10.0

    private fun hectares(value: Double): String =
        if (value < HECTARE_DECIMALS_BELOW) "${Formats.decimal(value, 1)} ha" else "${Formats.grouped(value.roundToLong())} ha"

    /**
     * "Very close to a drainage line, 0.4 m above a stream draining 21 ha": the class first, then the figures behind it (ADR 0040). This
     * says where water runs; it never says a lot floods.
     */
    fun drainage(hint: DrainageHint): String {
        val position = when (hint.proximity) {
            DrainageProximity.VERY_CLOSE -> "Very close to a drainage line"
            DrainageProximity.CLOSE -> "Close to a drainage line"
            DrainageProximity.MODERATE -> "Some way above a drainage line"
            DrainageProximity.WELL_ABOVE -> "Well above any drainage line"
        }
        return "$position, ${Formats.decimal(hint.heightAboveDrainageM, 1)} m above a stream draining ${hectares(hint.streamCatchmentHa)}"
    }

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
