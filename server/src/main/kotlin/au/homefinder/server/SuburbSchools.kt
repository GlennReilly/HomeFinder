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
