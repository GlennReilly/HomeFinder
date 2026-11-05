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

/** One suburb's values for the filter panel; each is null when unknown for the suburb (ADR 0046). */
data class SuburbFilterRow(
    val suburbId: String,
    val averageIcsea: Int?,
    val beachKm: Double? = null,
    val parkShare: Double? = null,
    val rentedShare: Double? = null,
    val seifaDecile: Int? = null,
    val violentPer1000: Double? = null,
    val propertyPer1000: Double? = null,
    val name: String = "",
)

/**
 * Every suburb's filter values and which data sets were loaded, with the day each was dated. A criterion's data set is "loaded" when its
 * table exists and has rows; its as-of date may still be unknown (null).
 */
data class SuburbFilterData(
    val suburbs: List<SuburbFilterRow>,
    val icseaAsOf: LocalDate?,
    val schoolsLoaded: Boolean = true,
    val osmLoaded: Boolean = false,
    val osmAsOf: LocalDate? = null,
    val censusLoaded: Boolean = false,
    val seifaLoaded: Boolean = false,
    val crimeLoaded: Boolean = false,
    val crimeAsOf: LocalDate? = null,
)
