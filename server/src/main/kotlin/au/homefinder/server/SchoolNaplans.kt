package au.homefinder.server

import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.NaplanScore
import au.homefinder.shared.model.School
import au.homefinder.shared.model.SuburbReport

/**
 * Puts your NAPLAN figures (ADR 0068) on the schools of a report: the Schools and Zoned schools rows of the Suburb Card and the Lot Card's
 * Schools row. Nothing else in the report changes, so the ICSEA roll-ups and filters stay as they were.
 */
object SchoolNaplans {
    fun codes(report: SuburbReport): Set<String> =
        (report.schools.value?.publicSchoolsInside.orEmpty() + report.schools.value?.catchments.orEmpty() + report.zonedSchools.value?.zones.orEmpty().map { it.school })
            .mapTo(mutableSetOf()) { it.id }

    fun codes(report: LotReport): Set<String> {
        val info = report.schools.value ?: return emptySet()
        return (listOfNotNull(info.primaryCatchment, info.highCatchment) + info.zoned + info.nearest.map { it.school }).mapTo(mutableSetOf()) { it.id }
    }

    fun attach(report: SuburbReport, scores: Map<String, NaplanScore>): SuburbReport {
        if (scores.isEmpty()) return report
        val schools = report.schools.value?.let { value ->
            report.schools.copy(value = value.copy(publicSchoolsInside = value.publicSchoolsInside.map { it.with(scores) }, catchments = value.catchments.map { it.with(scores) }))
        } ?: report.schools
        val zoned = report.zonedSchools.value?.let { value ->
            report.zonedSchools.copy(value = value.copy(zones = value.zones.map { it.copy(school = it.school.with(scores)) }))
        } ?: report.zonedSchools
        return report.copy(schools = schools, zonedSchools = zoned)
    }

    fun attach(report: LotReport, scores: Map<String, NaplanScore>): LotReport {
        val info = report.schools.value
        if (scores.isEmpty() || info == null) return report
        val updated = info.copy(
            primaryCatchment = info.primaryCatchment?.with(scores),
            highCatchment = info.highCatchment?.with(scores),
            nearest = info.nearest.map { it.copy(school = it.school.with(scores)) },
            zoned = info.zoned.map { it.with(scores) },
        )
        return report.copy(schools = report.schools.copy(value = updated))
    }

    private fun School.with(scores: Map<String, NaplanScore>): School = scores[id]?.let { copy(naplan = it) } ?: this
}
