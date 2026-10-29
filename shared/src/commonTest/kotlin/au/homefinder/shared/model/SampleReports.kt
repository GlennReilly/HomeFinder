package au.homefinder.shared.model

import kotlinx.datetime.LocalDate

/** Test data: a report with every attribute known, and one with every attribute unknown. */
object SampleReports {
    val asOf: LocalDate = LocalDate(2026, 10, 29)

    private val school = School("S1", "Hamilton Public School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY)
    private val highSchool = School("S2", "Newcastle High School", SchoolSector.PUBLIC, SchoolLevel.HIGH)

    private fun <T : Any> known(value: T, source: String, confidence: Confidence = Confidence.HIGH, note: String? = null) =
        Attribute(value, confidence, source, asOf, note)

    val fullProperty = PropertyReport(
        addressId = "GANSW721448653",
        lotId = "100016040",
        suburbId = "9001",
        wallMaterial = known(WallMaterial.BRICK_VENEER, "Listing text", Confidence.LOW, "Likely brick: 'brick veneer' in listing"),
        backyardAspect = known(AspectResult.of(350.0, LotShape.CORNER), "NSW cadastre and building footprint", Confidence.MEDIUM, "Corner lot, aspect ambiguous"),
        flood = known(FloodRisk(FloodClass.HIGH, aepPercent = 1.0), "Council flood study"),
        blockAreaM2 = known(612.5, "NSW Digital Cadastral Database"),
        slope = known(SlopeSummary(7.5, 14.0, SlopeClass.GENTLE, FallDirection.TO_REAR), "ELVIS DEM", Confidence.MEDIUM),
        estimatedPriceAud = known(
            PriceRange(
                lowAud = 850_000,
                midAud = 920_000,
                highAud = 990_000,
                comparables = listOf(
                    ComparableSale("12 Beaumont Street", LocalDate(2026, 8, 3), 905_000, landAreaM2 = 600.0, distanceM = 220),
                    ComparableSale("5 Tudor Street", LocalDate(2026, 6, 19), 940_000),
                ),
            ),
            "NSW Valuer General", Confidence.LOW,
        ),
        beach = known(Proximity("Merewether Beach", "beach", straightLineM = 2300, walkingM = 2900, walkingMinutes = 36), "OpenStreetMap"),
        schools = known(
            SchoolInfo(
                primaryCatchment = school,
                highCatchment = highSchool,
                nearest = listOf(NearbySchool(school, distanceM = 400, walkingMinutes = 6, drivingMinutes = 2)),
            ),
            "NSW Department of Education",
        ),
        parks = known(ParkAccess(listOf(Proximity("Gregson Park", "park", 150, 180, 3)), areaWithin800mM2 = 51_000.0), "OpenStreetMap", Confidence.MEDIUM),
        shopping = known(ProximityList(listOf(Proximity("Hamilton Marketplace", "supermarket", 600, 700, 9))), "OpenStreetMap", Confidence.MEDIUM),
        transport = known(
            TransportInfo(listOf(TransportStop(TransportMode.TRAIN, "Broadmeadow", 900, 11, 4.0)), travelToWorkMinutes = 25),
            "Transport for NSW",
        ),
    )

    private fun <T> unknown(source: String) = Attribute.unknown<T>(source, note = "no data")

    val unknownProperty = PropertyReport(
        addressId = "GANSW1",
        lotId = "1",
        suburbId = "9001",
        wallMaterial = unknown("Listing text"),
        backyardAspect = unknown("NSW cadastre"),
        flood = unknown("Council flood study"),
        blockAreaM2 = unknown("NSW Digital Cadastral Database"),
        slope = unknown("ELVIS DEM"),
        estimatedPriceAud = unknown("NSW Valuer General"),
        beach = unknown("OpenStreetMap"),
        schools = unknown("NSW Department of Education"),
        parks = unknown("OpenStreetMap"),
        shopping = unknown("OpenStreetMap"),
        transport = unknown("Transport for NSW"),
    )

    val fullSuburb = SuburbReport(
        suburbId = "9001",
        name = "Hamilton",
        postcode = "2303",
        areaKm2 = known(3.512, "NSW Spatial Services suburb boundary"),
        medianHousePriceAud = known(1_050_000L, "NSW Valuer General", Confidence.MEDIUM),
        medianUnitPriceAud = known(640_000L, "NSW Valuer General", Confidence.MEDIUM),
        priceChange12MonthsPercent = known(4.2, "NSW Valuer General", Confidence.MEDIUM),
        salesCount = known(187, "NSW Valuer General"),
        blockSize = known(BlockSizeSpread(medianM2 = 560.0, p10M2 = 320.0, p90M2 = 810.0), "NSW Digital Cadastral Database"),
        floodShare = known(SuburbFloodShare(lotsInFloodExtentShare = 0.18, mappedCoverageShare = 0.9), "Council flood study", Confidence.MEDIUM),
        brickShare = known(SuburbBrickShare(likelyBrickShare = 0.42, evidenceShare = 0.3), "Listing text", Confidence.LOW),
        beach = known(SuburbBeachDistance(fromCentreM = 3500, fromNearestEdgeM = 2400), "OpenStreetMap"),
        schools = known(SuburbSchools(listOf(school), listOf(school, highSchool)), "NSW Department of Education"),
        parkAreaShare = known(0.07, "OpenStreetMap", Confidence.MEDIUM),
        shopping = known(SuburbShopping(shoppingCentres = 2, supermarkets = 5), "OpenStreetMap", Confidence.MEDIUM),
        transport = known(SuburbTransport(2, 0, 0, 96, bestStopPeakDeparturesPerHour = 8.0), "Transport for NSW"),
    )

    val unknownSuburb = SuburbReport(
        suburbId = "9002",
        name = "Belmont",
        postcode = null,
        areaKm2 = unknown("NSW Spatial Services suburb boundary"),
        medianHousePriceAud = unknown("NSW Valuer General"),
        medianUnitPriceAud = unknown("NSW Valuer General"),
        priceChange12MonthsPercent = unknown("NSW Valuer General"),
        salesCount = unknown("NSW Valuer General"),
        blockSize = unknown("NSW Digital Cadastral Database"),
        floodShare = unknown("Council flood study"),
        brickShare = unknown("Listing text"),
        beach = unknown("OpenStreetMap"),
        schools = unknown("NSW Department of Education"),
        parkAreaShare = unknown("OpenStreetMap"),
        shopping = unknown("OpenStreetMap"),
        transport = unknown("Transport for NSW"),
    )
}
