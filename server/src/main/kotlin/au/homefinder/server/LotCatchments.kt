package au.homefinder.server

/**
 * One catchment (intake zone) a lot lies in (ADR 0056). [level] is PRIMARY or HIGH (which of the Department's two files it came from);
 * [catchType] is the Department's own type, for example INFANTS or CENTRAL_HIGH; [school] is the matching row of the public schools data, or
 * null where the school code is not in it (the card then uses the Department's short [name]).
 */
data class CatchmentZone(
    val schoolCode: Int,
    val level: String,
    val catchType: String,
    val name: String,
    val years: String?,
    val enrolmentYear: Int?,
    val school: SchoolRecord?,
)

/** The catchments covering a lot: possibly none (the lot is in no zone) or several (an infants zone inside a primary zone, a central school's two zones). */
data class LotCatchmentData(val zones: List<CatchmentZone>)
