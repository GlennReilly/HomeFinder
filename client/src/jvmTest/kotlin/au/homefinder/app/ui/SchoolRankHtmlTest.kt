package au.homefinder.app.ui

import au.homefinder.app.selection.LotCardState
import au.homefinder.app.selection.SuburbCardState
import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.NearbySchool
import au.homefinder.shared.model.School
import au.homefinder.shared.model.SchoolInfo
import au.homefinder.shared.model.SchoolLevel
import au.homefinder.shared.model.SchoolSector
import au.homefinder.shared.model.SuburbReport
import au.homefinder.shared.model.SuburbSchools
import au.homefinder.shared.model.SuburbZone
import au.homefinder.shared.model.SuburbZonedSchools
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchoolRankHtmlTest {
    private fun <T> u() = Attribute.unknown<T>("src", note = "Not computed yet")
    private fun school(id: String, name: String, locality: String?, level: SchoolLevel = SchoolLevel.PRIMARY) =
        School(id, name, SchoolSector.PUBLIC, level, 1000, locality = locality)

    private val elermore = school("4163", "Elermore Vale Public School", "Wallsend")
    private val noLocality = school("9", "Unknown Place High School", null, SchoolLevel.HIGH)

    private val suburb = SuburbReport(
        "9001", "ELERMORE VALE", "2287", Attribute(1.0, Confidence.HIGH, "src"), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(),
    ).copy(
        schools = Attribute(SuburbSchools(listOf(elermore), emptyList(), 1000), Confidence.HIGH, "src"),
        zonedSchools = Attribute(SuburbZonedSchools(listOf(SuburbZone(elermore, 0.9), SuburbZone(noLocality, 0.8))), Confidence.MEDIUM, "src"),
    )

    private val href = "https://schoolrank.com.au/school/elermore-vale-public-school-wallsend-nsw"
    private fun links(out: String) = Regex("""<a [^>]*>${SCHOOLRANK_LINK_TEXT}</a>""").findAll(out).map { it.value }.toList()

    @Test
    fun `each listing of a school with a locality gets a SchoolRank link that opens in a new tab`() {
        val out = createHTML().div { suburbCard(SuburbCardState.Loaded(suburb), groupOpen = mapOf("Schools" to true)) }
        val found = links(out)
        assertEquals(2, found.size, "the Schools row and the Zoned schools row, not the school without a locality: $found")
        found.forEach { link ->
            assertTrue("""href="$href"""" in link, link)
            assertTrue("""target="_blank"""" in link && """rel="noopener noreferrer"""" in link && """class="external-link"""" in link, link)
            assertTrue("""title="SchoolRank page for Elermore Vale Public School (opens in a new tab; address built by us, may not exist)"""" in link, link)
        }
        assertFalse("unknown-place-high-school" in out)
    }

    @Test
    fun `the lot card's schools get the link too`() {
        val lot = LotReport("1001", null, "9001", "ELERMORE VALE", "2287", listOf("1 MAIN ROAD"), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u()).copy(
            schools = Attribute(SchoolInfo(nearest = listOf(NearbySchool(noLocality, 900)), zoned = listOf(elermore), zonesChecked = true), Confidence.MEDIUM, "src"),
        )
        val out = createHTML().div { lotCard(LotCardState.Loaded(lot), groupOpen = mapOf("Nearby" to true)) }
        assertEquals(1, links(out).size)
        assertTrue(href in out)
    }
}
