package au.homefinder.server

import kotlinx.datetime.LocalDate

/** A public school and how far it is from a lot (straight line, in metres, to the lot's nearest edge). */
data class NearSchool(val school: SchoolRecord, val distanceM: Double)

/**
 * The public schools closest to a lot, nearest first (ADR 0045), and the day the school data was extracted. The directory returns a few
 * dozen candidates; which of them the card shows is decided in [LotReportFactory].
 */
data class LotSchoolData(val candidates: List<NearSchool>, val extractedOn: LocalDate?)
