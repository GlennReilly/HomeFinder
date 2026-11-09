package au.homefinder.server

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.BlockSizeSpread
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.HomelyRating
import au.homefinder.shared.model.NaplanScore
import au.homefinder.shared.model.School
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolSector
import au.homefinder.shared.model.SuburbReport
import au.homefinder.shared.model.SuburbSchools
import au.homefinder.shared.model.SuburbShopping
import au.homefinder.shared.model.SuburbZone
import au.homefinder.shared.model.SuburbZonedSchools
import kotlinx.datetime.LocalDate

/** Suburb reports for the export tests: prices, block size, parks, shopping and two zoned schools are known; the rest is unknown. */
object ExportSamples {
    private fun <T> unknown() = Attribute.unknown<T>("test", note = "Not computed yet")

    fun report(id: String = "9001", name: String = "HAMILTON", postcode: String? = "2303", rated: Boolean = false, naplan: Int? = 5): SuburbReport = SuburbReport(
        suburbId = id, name = name, postcode = postcode, areaKm2 = Attribute(1.46, Confidence.HIGH, "Test boundary"),
        medianHousePriceAud = Attribute(900_000L, Confidence.HIGH, "Test sales", LocalDate(2026, 10, 9)),
        medianUnitPriceAud = Attribute(600_000L, Confidence.MEDIUM, "Test sales", LocalDate(2026, 10, 9)),
        priceChange12MonthsPercent = Attribute(4.2, Confidence.MEDIUM, "Test sales", LocalDate(2026, 10, 9)),
        salesCount = Attribute(120, Confidence.HIGH, "Test sales", LocalDate(2026, 10, 9)),
        blockSize = Attribute(BlockSizeSpread(560.0, 300.0, 900.0), Confidence.HIGH, "Test cadastre"),
        floodShare = unknown(), brickShare = unknown(), beach = unknown(),
        schools = Attribute(SuburbSchools(emptyList(), emptyList(), null), Confidence.HIGH, "Test schools"),
        zonedSchools = Attribute(
            SuburbZonedSchools(
                listOf(
                    SuburbZone(School("1", "Hamilton South Public School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, 1041, null, null, "primary, K-6", naplan?.let { NaplanScore(it) }), 0.82),
                    SuburbZone(School("2", "Newcastle High School", SchoolSector.PUBLIC, SchoolLevel.HIGH, 1017, null, null, "high, 7-12"), 0.76),
                ),
                2026,
            ),
            Confidence.MEDIUM, "Test catchments", null, "Catchments change",
        ),
        parkAreaShare = Attribute(0.12, Confidence.MEDIUM, "Test OpenStreetMap", LocalDate(2026, 10, 29)),
        shopping = Attribute(SuburbShopping(1, 2), Confidence.MEDIUM, "Test OpenStreetMap", LocalDate(2026, 10, 29)),
        transport = unknown(),
        homelyRating = if (rated) Attribute(HomelyRating(4.1, 7), Confidence.LOW, HomelyRating.SOURCE, LocalDate(2026, 10, 30)) else Attribute.unknown(HomelyRating.SOURCE),
    )
}
