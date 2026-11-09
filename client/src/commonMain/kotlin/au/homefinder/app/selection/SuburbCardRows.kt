package au.homefinder.app.selection

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.BlockSizeSpread
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.CrimeRate
import au.homefinder.shared.model.DwellingMix
import au.homefinder.shared.model.SuburbBeachDistance
import au.homefinder.shared.model.SuburbReport
import au.homefinder.shared.model.SuburbZone
import au.homefinder.shared.model.SuburbZonedSchools
import au.homefinder.shared.model.School
import au.homefinder.shared.model.NaplanRules
import au.homefinder.shared.model.NaplanScore
import au.homefinder.shared.model.SchoolLevel
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

    /** The row the Homely score and its entry form sit under (ADR 0030). */
    const val MEDIAN_HOUSE_LABEL: String = "Median house price"
    const val MEDIAN_UNIT_LABEL: String = "Median unit price"

    const val PRICE_CHANGE_LABEL: String = "Price change, 12 months"

    /**
     * The rows in the order you asked for: the prices, with the typical block size straight under the median house price. The
     * suburb's area is not shown (it stays in the report for the map). Flood and Likely brick are not shown either: they have no
     * reliable source (ADR 0041), though their fields stay in the report.
     */
    fun of(report: SuburbReport): List<CardRow> = listOf(
        row(MEDIAN_HOUSE_LABEL, report.medianHousePriceAud, Formats::dollars),
        row("Typical block size", report.blockSize, ::blockSize),
        row(MEDIAN_UNIT_LABEL, report.medianUnitPriceAud, Formats::dollars),
        priceChange(report),
        row("Socio-economic advantage", report.socioEconomic) { "Decile ${it.decile} of 10 (10 is the most advantaged)" },
        row("Disadvantage (SEIFA)", report.disadvantage) { "Decile $it of 10 (10 is the least disadvantaged)" },
        row("Economic resources (SEIFA)", report.economicResources) { "Decile $it of 10 (10 has the most resources)" },
        row("Education and occupation (SEIFA)", report.educationOccupation) { "Decile $it of 10 (10 is the highest)" },
        row("Homes rented", report.rentedShare) { Formats.share(it) + " of homes are rented" },
        row("Houses and units", report.dwellingMix, ::dwellingMix),
        row("Violent crime", report.violentCrime, ::crime),
        row("Break-ins, car and home theft", report.propertyCrime, ::crime),
        row("Distance to the beach", report.beach, ::beach),
        withNaplanSource(row(SCHOOLS_LABEL, report.schools, ::schools), report.schools.value?.publicSchoolsInside.orEmpty()),
        withNaplanSource(row(ZONED_LABEL, report.zonedSchools, ::zonedSchools), report.zonedSchools.value?.zones.orEmpty().map { it.school }),
        row("Parks", report.parkAreaShare) { Formats.share(it) + " of the suburb is park" },
        row("Shopping", report.shopping, ::shopping),
        row("Transport", report.transport, ::transport),
    )

    /**
     * "+4.2% (31 sales)": the change in the house median, with the number of sales behind the prices in brackets (ADR 0059; it was a row of its
     * own). The note keeps the change's own reasoning and then the house/unit split of those sales. While the change is unknown the value stays
     * empty and the note is the reason, which already carries the counts.
     */
    internal fun priceChange(report: SuburbReport): CardRow {
        val change = report.priceChange12MonthsPercent
        val base = row(PRICE_CHANGE_LABEL, change, Formats::signedPercent)
        val sales = report.salesCount.takeUnless { it.isUnknown }?.value ?: return base
        val value = base.value ?: return base
        val count = Formats.grouped(sales.toLong()) + if (sales == 1) " sale" else " sales"
        // The change's note has no closing full stop, the sales note ends with one: join them as two sentences.
        val note = listOfNotNull(base.note?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() }, report.salesCount.note?.trim()?.takeIf { it.isNotEmpty() })
        return base.copy(value = "$value ($count)", note = note.joinToString(". ").ifEmpty { null })
    }

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

    /** A schools row whose schools include a NAPLAN figure you typed in says so in its Source line (ADR 0068; neutral wording, ADR 0069). */
    internal fun withNaplanSource(row: CardRow, schools: List<School>): CardRow =
        if (schools.any { it.naplan != null }) row.copy(source = "${row.source}; ${NaplanScore.SOURCE}") else row

    /** "ICSEA 1,051", a school's ICSEA line, or null where ACARA publishes none (then the line is left out). */
    fun icseaText(school: School): String? = school.icsea?.let { "ICSEA ${Formats.grouped(it.toLong())}" }


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

    /** "58% houses, 25% townhouses, 15% units": separate houses, then semis and townhouses, then flats and apartments (2021 Census, ADR 0044). */
    private fun dwellingMix(mix: DwellingMix) =
        "${Formats.share(mix.houseShare)} houses, ${Formats.share(mix.townhouseShare)} townhouses, ${Formats.share(mix.unitShare)} units"

    /** "4.2 per 1,000 residents (31 incidents in 24 months)", or just the count where there is no population to divide by. */
    private fun crime(rate: CrimeRate): String {
        val counted = "${Formats.grouped(rate.incidents.toLong())} ${if (rate.incidents == 1) "incident" else "incidents"} in ${rate.months} months"
        return rate.perThousand?.let { "${Formats.decimal(it, 1)} per 1,000 residents ($counted)" } ?: "$counted, no population to turn it into a rate"
    }

    private fun beach(distance: SuburbBeachDistance) =
        "${Formats.kilometres(distance.fromCentreM)} from the centre, ${Formats.kilometres(distance.fromNearestEdgeM)} from the nearest edge"

    const val SCHOOLS_LABEL: String = "Schools"
    const val ZONED_LABEL: String = "Zoned schools"

    /** "Hamilton South Public School (primary), Newcastle High School (high)": the zones by name, the detail (share, years, ICSEA) is listed under the row. */
    private fun zonedSchools(zoned: SuburbZonedSchools): String =
        zoned.zones.joinToString(", ") { "${it.school.name} (${zoneLevel(it.school)})" }

    /** The kind of zone in a word or two: "primary", "high, girls", "central school, primary years" (the first part of the label). */
    private fun zoneLevel(school: School): String = school.levelLabel?.substringBefore(", ").orEmpty().ifBlank { if (school.level == SchoolLevel.HIGH) "high" else "primary" }

    /** One zone's first line under the Zoned schools row: its kind and years and the share of the suburb's lots it covers (ICSEA and NAPLAN have lines of their own, ADR 0071). */
    fun zoneDetail(zone: SuburbZone): String = listOfNotNull(
        zone.school.levelLabel,
        "${Formats.share(zone.lotShare)} of the suburb's lots",
    ).joinToString(" · ")

    private fun schools(schools: SuburbSchools): String {
        val count = schools.publicSchoolsInside.size
        if (count == 0) return "No public schools inside the suburb"
        val average = schools.averageIcsea?.let { ", average ICSEA ${Formats.grouped(it.toLong())} (1,000 is average)" }.orEmpty()
        return (if (count == 1) "1 public school" else "$count public schools") + average
    }

    /** One school's first line under the Schools row: its kind and size (ICSEA and NAPLAN have lines of their own, ADR 0071). */
    fun schoolDetail(school: School): String = listOfNotNull(
        school.levelLabel ?: when (school.level) { SchoolLevel.PRIMARY -> "Primary"; SchoolLevel.HIGH -> "High school"; SchoolLevel.COMBINED -> "Combined" },
        school.enrolments?.let { "${Formats.grouped(it.toLong())} students" },
    ).joinToString(" · ")

    private fun shopping(shopping: SuburbShopping) =
        "${shopping.shoppingCentres} shopping centres and ${shopping.supermarkets} supermarkets, inside or within 2 km"

    private fun transport(transport: SuburbTransport): String {
        val counts = "${transport.trainStations} train, ${transport.lightRailStations} light rail, ${transport.ferryWharves} ferry, ${transport.busStops} bus stops"
        val best = transport.bestStopPeakDeparturesPerHour?.let { "; best stop ${Formats.decimal(it, 0)} an hour at peak" }.orEmpty()
        return counts + best
    }
}
