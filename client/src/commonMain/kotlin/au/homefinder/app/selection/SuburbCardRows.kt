package au.homefinder.app.selection

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.BlockSizeSpread
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.SuburbBeachDistance
import au.homefinder.shared.model.SuburbBrickShare
import au.homefinder.shared.model.SuburbFloodShare
import au.homefinder.shared.model.SuburbReport
import au.homefinder.shared.model.SuburbSchools
import au.homefinder.shared.model.SuburbShopping
import au.homefinder.shared.model.SuburbTransport
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** One line of the Suburb Card. A null [value] is shown as "Unknown": missing data is never a silent blank. */
data class CardRow(
    val label: String,
    val value: String?,
    val source: String,
    val asOf: String?,
    val note: String?,
    val confidence: Confidence,
) {
    val isUnknown: Boolean get() = value == null
}

object Formats {
    private const val PERCENT = 100.0
    private const val GROUP_SIZE = 3
    private const val METRES_PER_KM = 1000.0

    fun decimal(value: Double, places: Int): String {
        val scale = 10.0.pow(places)
        val scaled = (abs(value) * scale).roundToLong()
        val whole = scaled / scale.toLong()
        val fraction = (scaled % scale.toLong()).toString().padStart(places, '0')
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        return if (places == 0) "$sign$whole" else "$sign$whole.$fraction"
    }

    fun grouped(value: Long): String {
        val digits = abs(value).toString()
        val groups = digits.reversed().chunked(GROUP_SIZE).joinToString(",").reversed()
        return if (value < 0) "-$groups" else groups
    }

    fun dollars(value: Long): String = "$" + grouped(value)

    fun signedPercent(percent: Double): String = (if (percent > 0) "+" else "") + decimal(percent, 1) + "%"

    /** A fraction from 0 to 1 as a whole-number percentage. */
    fun share(fraction: Double): String = decimal(fraction * PERCENT, 0) + "%"

    fun kilometres(metres: Int): String = decimal(metres / METRES_PER_KM, 1) + " km"

    /** Whole metres under a kilometre, otherwise kilometres to one place. */
    fun distance(metres: Int): String = if (metres < METRES_PER_KM.toInt()) "$metres m" else kilometres(metres)

    fun squareMetres(value: Double): String = grouped(value.roundToLong()) + " m²"
}

object SuburbCardRows {
    const val UNKNOWN: String = "Unknown"

    /** The rows in the order the spec lists the Suburb Card contents. */
    fun of(report: SuburbReport): List<CardRow> = listOf(
        row("Area", report.areaKm2) { Formats.decimal(it, 2) + " km²" },
        row("Median house price", report.medianHousePriceAud, Formats::dollars),
        row("Median unit price", report.medianUnitPriceAud, Formats::dollars),
        row("Price change, 12 months", report.priceChange12MonthsPercent, Formats::signedPercent),
        row("Sales behind the prices", report.salesCount) { Formats.grouped(it.toLong()) },
        row("Typical block size", report.blockSize, ::blockSize),
        row("Flood", report.floodShare, ::flood),
        row("Likely brick", report.brickShare, ::brick),
        row("Distance to the beach", report.beach, ::beach),
        row("Schools", report.schools, ::schools),
        row("Parks", report.parkAreaShare) { Formats.share(it) + " of the suburb is park" },
        row("Shopping", report.shopping, ::shopping),
        row("Transport", report.transport, ::transport),
    )

    /**
     * The Homely score you entered, as a row, or null when there is none (the card then shows only the form). It reads
     * "2.1 ★ (7 reviews)"; its provenance line says it was entered by you.
     */
    fun homely(report: SuburbReport): CardRow? {
        if (report.homelyRating.isUnknown) return null
        return row("Homely rating", report.homelyRating) { rating ->
            val reviews = rating.reviewCount?.let { " (${Formats.grouped(it.toLong())} ${if (it == 1) "review" else "reviews"})" }.orEmpty()
            Formats.decimal(rating.stars, 1) + " ★" + reviews
        }
    }

    internal fun <T> row(label: String, attribute: Attribute<T>, format: (T) -> String): CardRow = CardRow(
        label = label,
        value = attribute.value?.let(format),
        source = attribute.source,
        asOf = attribute.asOf?.toString(),
        note = attribute.note,
        confidence = attribute.confidence,
    )

    private fun blockSize(spread: BlockSizeSpread) =
        "${Formats.squareMetres(spread.medianM2)} median (${Formats.grouped(spread.p10M2.roundToLong())} to ${Formats.squareMetres(spread.p90M2)})"

    private fun flood(share: SuburbFloodShare) =
        "${Formats.share(share.lotsInFloodExtentShare)} of lots touch a mapped flood extent; ${Formats.share(share.mappedCoverageShare)} of the suburb has flood mapping"

    private fun brick(share: SuburbBrickShare) =
        "${Formats.share(share.likelyBrickShare)} of lots are likely brick; ${Formats.share(share.evidenceShare)} have enough evidence to say"

    private fun beach(distance: SuburbBeachDistance) =
        "${Formats.kilometres(distance.fromCentreM)} from the centre, ${Formats.kilometres(distance.fromNearestEdgeM)} from the nearest edge"

    private fun schools(schools: SuburbSchools) =
        "${schools.publicSchoolsInside.size} public schools inside; ${schools.catchments.size} catchments cover it"

    private fun shopping(shopping: SuburbShopping) =
        "${shopping.shoppingCentres} shopping centres and ${shopping.supermarkets} supermarkets, inside or within 2 km"

    private fun transport(transport: SuburbTransport): String {
        val counts = "${transport.trainStations} train, ${transport.lightRailStations} light rail, ${transport.ferryWharves} ferry, ${transport.busStops} bus stops"
        val best = transport.bestStopPeakDeparturesPerHour?.let { "; best stop ${Formats.decimal(it, 0)} an hour at peak" }.orEmpty()
        return counts + best
    }
}
