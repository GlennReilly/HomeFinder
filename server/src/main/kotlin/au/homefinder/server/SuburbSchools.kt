package au.homefinder.server

import kotlinx.datetime.LocalDate

/** A public school as the pipeline stored it (ADR 0034). [icsea] and [enrolments] are null where they are not published. */
data class SchoolRecord(
    val code: Int,
    val name: String,
    val levelLabel: String?,
    val icsea: Int?,
    val enrolments: Int?,
    val website: String?,
)

/** The public schools inside one suburb and the date the school data was extracted; [schools] may be empty. */
data class SuburbSchoolData(val schools: List<SchoolRecord>, val extractedOn: LocalDate?)

/** One suburb's values for the filter panel; the average is null when it has no school with an ICSEA. */
data class SuburbFilterRow(val suburbId: String, val averageIcsea: Int?)

/** Every suburb's filter values and the day the schools data was extracted (null when unknown). */
data class SuburbFilterData(val suburbs: List<SuburbFilterRow>, val icseaAsOf: LocalDate?)
