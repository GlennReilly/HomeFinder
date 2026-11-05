package au.homefinder.app.selection

import au.homefinder.shared.model.DrainageHint
import au.homefinder.shared.model.DrainageProximity
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.ParkAccess
import au.homefinder.shared.model.Proximity
import au.homefinder.shared.model.ProximityList
import au.homefinder.shared.model.School
import au.homefinder.shared.model.SchoolInfo
import au.homefinder.shared.model.SlopeSummary
import kotlin.math.roundToLong

/**
 * The rows of the basic lot card (ADR 0022): the measured block size, then the other factors in the spec's order. Wall material, Flood and
 * Estimated price are not shown because nothing reliable can fill them (ADR 0041); their fields stay in the report.
 */
object LotCardRows {
    /** What the Height above drainage row says where the suburb is too flat for the hint (ADR 0054). */
    const val NOT_SUITABLE_FLAT_TEXT: String = "Not suitable for flat areas"

    /**
     * The block size and the OpenStreetMap proximity factors (ADR 0023) have values. The remaining factors are
     * formatted with a plain fallback; each gets its own wording when its pipeline lands and a real value can reach the card.
     */
    fun of(report: LotReport): List<CardRow> = listOf(
        SuburbCardRows.row("Block size", report.blockAreaM2, Formats::squareMetres),
        SuburbCardRows.row("Backyard aspect", report.backyardAspect) { it.toString() },
        SuburbCardRows.row("Slope", report.slope, ::slope),
        SuburbCardRows.row("Height above sea level", report.elevationM, ::height),
        SuburbCardRows.row("Height above drainage", report.heightAboveDrainage, ::drainage),
        SuburbCardRows.row("Distance to the beach", report.beach, ::beach),
        SuburbCardRows.row(SuburbCardRows.SCHOOLS_LABEL, report.schools, ::schools),
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
        if (hint.proximity == DrainageProximity.FLAT_AREA) return NOT_SUITABLE_FLAT_TEXT
        val position = when (hint.proximity) {
            DrainageProximity.VERY_CLOSE -> "Very close to a drainage line"
            DrainageProximity.CLOSE -> "Close to a drainage line"
            DrainageProximity.MODERATE -> "Some way above a drainage line"
            DrainageProximity.WELL_ABOVE -> "Well above any drainage line"
            DrainageProximity.FLAT_AREA -> NOT_SUITABLE_FLAT_TEXT
        }
        return "$position, ${Formats.decimal(hint.heightAboveDrainageM, 1)} m above a stream draining ${hectares(hint.streamCatchmentHa)}"
    }

    /**
     * "Hamilton Public School (Primary School), 600 m away, about 8 min walk (estimated), ICSEA 1,051; ..." for the nearest primary and the
     * nearest high school (ADR 0045). ICSEA is only said where ACARA publishes one.
     */
    private fun schools(info: SchoolInfo): String {
        val parts = ArrayList<String>()
        if (info.zoned.isNotEmpty()) parts += "Zoned: " + info.zoned.joinToString(", ") { zone -> zoned(zone) }
        if (info.nearest.isNotEmpty()) parts += (if (info.zonesChecked) NEAREST_NOT_ZONED_PREFIX else "") + nearest(info)
        return parts.joinToString("; ").ifEmpty { "No public school found nearby" }
    }

    /** What the nearest schools are called where the lot has a zone for the other level or none at all (ADR 0056). */
    const val NEAREST_NOT_ZONED_PREFIX: String = "Nearest, not zoned: "

    /** "Hamilton Public School (primary, K-6), ICSEA 1,051": a school whose catchment the lot lies in, with the kind and years of the zone. */
    private fun zoned(school: School): String {
        val kind = school.levelLabel?.let { " ($it)" }.orEmpty()
        val icsea = school.icsea?.let { ", ICSEA ${Formats.grouped(it.toLong())}" }.orEmpty()
        return "${school.name}$kind$icsea"
    }

    private fun nearest(info: SchoolInfo): String {
        return info.nearest.joinToString("; ") { near ->
            val school = near.school
            val kind = school.levelLabel?.let { " ($it)" }.orEmpty()
            val walk = near.walkingMinutes?.takeIf { it <= WALK_ESTIMATE_MAX_MINUTES }?.let { ", about $it min walk (estimated)" }.orEmpty()
            val icsea = school.icsea?.let { ", ICSEA ${Formats.grouped(it.toLong())}" }.orEmpty()
            "${school.name}$kind, ${Formats.distance(near.distanceM)} away$walk$icsea"
        }
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
