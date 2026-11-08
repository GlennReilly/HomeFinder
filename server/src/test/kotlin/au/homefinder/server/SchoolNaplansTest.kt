package au.homefinder.server

import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.NaplanScore
import au.homefinder.shared.model.NearbySchool
import au.homefinder.shared.model.School
import au.homefinder.shared.model.SchoolInfo
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolSector
import au.homefinder.shared.model.SuburbReport
import au.homefinder.shared.model.SuburbSchools
import au.homefinder.shared.model.SuburbZone
import au.homefinder.shared.model.SuburbZonedSchools
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SchoolNaplansTest {
    private fun <T> u() = Attribute.unknown<T>("src")
    private fun school(id: String, level: SchoolLevel = SchoolLevel.PRIMARY) = School(id, "School $id", SchoolSector.PUBLIC, level, 1000)
    private val score = NaplanScore(512, 2025, null, LocalDate(2026, 11, 8))

    private val suburb = SuburbReport(
        "9001", "HAMILTON", "2303", Attribute(1.0, Confidence.HIGH, "src"), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(),
    ).copy(
        schools = Attribute(SuburbSchools(listOf(school("1"), school("2")), emptyList(), 1000), Confidence.HIGH, "src"),
        zonedSchools = Attribute(SuburbZonedSchools(listOf(SuburbZone(school("1"), 0.8), SuburbZone(school("3", SchoolLevel.HIGH), 0.7))), Confidence.MEDIUM, "src"),
    )

    private val lot = LotReport("1001", null, "9001", "HAMILTON", "2303", emptyList(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u()).copy(
        schools = Attribute(SchoolInfo(nearest = listOf(NearbySchool(school("4", SchoolLevel.HIGH), 900)), zoned = listOf(school("1")), zonesChecked = true), Confidence.MEDIUM, "src"),
    )

    @Test
    fun `the codes are every school the cards list`() {
        assertEquals(setOf("1", "2", "3"), SchoolNaplans.codes(suburb))
        assertEquals(setOf("1", "4"), SchoolNaplans.codes(lot))
        assertEquals(emptySet(), SchoolNaplans.codes(lot.copy(schools = u())))
    }

    @Test
    fun `a figure goes on each listing of its school on the Suburb Card and nothing else changes`() {
        val attached = SchoolNaplans.attach(suburb, mapOf("1" to score, "3" to score))
        assertEquals(listOf(score, null), attached.schools.value!!.publicSchoolsInside.map { it.naplan })
        assertEquals(listOf(score, score), attached.zonedSchools.value!!.zones.map { it.school.naplan })
        assertEquals(suburb.schools.value!!.averageIcsea, attached.schools.value!!.averageIcsea)
        assertEquals(suburb.copy(schools = attached.schools, zonedSchools = attached.zonedSchools), attached)
    }

    @Test
    fun `a figure goes on the lot's zoned and nearest schools`() {
        val attached = SchoolNaplans.attach(lot, mapOf("4" to score))
        assertEquals(score, attached.schools.value!!.nearest.single().school.naplan)
        assertNull(attached.schools.value!!.zoned.single().naplan)
    }

    @Test
    fun `with no figures the report is left alone`() {
        assertSame(suburb, SchoolNaplans.attach(suburb, emptyMap()))
        assertSame(lot, SchoolNaplans.attach(lot, emptyMap()))
    }
}
