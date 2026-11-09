package au.homefinder.app.ui

import au.homefinder.app.selection.LotCardState
import au.homefinder.app.selection.SuburbCardRows
import au.homefinder.app.selection.SuburbCardState
import au.homefinder.app.selection.LotCardRows
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
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaplanHtmlTest {
    private fun <T> u() = Attribute.unknown<T>("src", note = "Not computed yet")
    private val score = NaplanScore(-41, 2025, "Year 5 reading", LocalDate(2026, 11, 8))
    private fun school(id: String, name: String, naplan: NaplanScore? = null, level: SchoolLevel = SchoolLevel.PRIMARY) =
        School(id, name, SchoolSector.PUBLIC, level, 1051, naplan = naplan)

    private fun suburb(scored: Boolean) = SuburbReport(
        "9001", "HAMILTON", "2303", Attribute(1.0, Confidence.HIGH, "src"), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(),
    ).copy(
        schools = Attribute(SuburbSchools(listOf(school("1", "Hamilton Public School", score.takeIf { scored })), emptyList(), 1051), Confidence.HIGH, "Department of Education"),
        zonedSchools = Attribute(
            SuburbZonedSchools(listOf(SuburbZone(school("1", "Hamilton Public School", score.takeIf { scored }), 0.8), SuburbZone(school("2", "Newcastle High School", level = SchoolLevel.HIGH), 0.7))),
            Confidence.MEDIUM, "catchments",
        ),
    )

    private fun suburbHtml(scored: Boolean, open: String? = null) =
        createHTML().div { suburbCard(SuburbCardState.Loaded(suburb(scored)), groupOpen = mapOf("Schools" to true), naplanOpen = open) }

    private fun ids(out: String) = Regex("""id="(naplan-edit-[^"]*)"""").findAll(out).map { it.groupValues[1] }.toList()

    @Test
    fun `every school listed gets its own Add NAPLAN link, and a school listed twice never repeats an id`() {
        val out = suburbHtml(scored = false)
        assertEquals(listOf("naplan-edit-1", "naplan-edit-1-2", "naplan-edit-2"), ids(out))
        assertEquals(3, Regex(">$NAPLAN_ADD_TEXT<").findAll(out).count())
        assertFalse("vs national" in out)
        assertFalse(NaplanIds.FORM in out, "no form until a link is pressed")
        assertFalse("Your own figure" in out, "the Source wording goes with a figure")
    }

    @Test
    fun `a saved figure shows beside the ICSEA in both rows, the link says Edit and the Source says it is your own figure`() {
        val out = suburbHtml(scored = true)
        assertTrue("Primary · ICSEA 1,051 · NAPLAN -41 vs national (2025)" in out, out)
        assertTrue("ICSEA 1,051 · NAPLAN -41 vs national (2025)" in out.substringAfter("80% of the suburb"))
        assertEquals(2, Regex(">$NAPLAN_EDIT_TEXT<").findAll(out).count())
        assertTrue("Department of Education; Your own figure, typed in. NAPLAN results are published by ACARA on My School." in out)
        assertFalse("Source: Australian Curriculum" in out, "the app does not say the figure came from ACARA")
        // The second school has no figure: still just the Add link.
        assertTrue("aria-label=\"$NAPLAN_ADD_TEXT for Newcastle High School\"" in out)
    }

    @Test
    fun `the open form sits under the school whose link was pressed, filled with the saved figure`() {
        val out = suburbHtml(scored = true, open = "naplan-edit-1-2")
        assertEquals(1, Regex("""id="${NaplanIds.FORM}"""").findAll(out).count())
        val form = out.substringAfter("""id="naplan-edit-1-2"""")
        assertTrue(form.indexOf(NaplanIds.FORM) < form.indexOf("naplan-edit-2"), "the form comes before the next school")
        assertTrue(out.substringBefore("""id="naplan-edit-1-2"""").contains(NaplanIds.FORM).not(), "not under the first listing")
        listOf(NaplanIds.SCORE, NaplanIds.YEAR, NaplanIds.NOTE, NaplanIds.SAVE, NaplanIds.CLEAR, NaplanIds.CANCEL, NaplanIds.MESSAGE).forEach {
            assertTrue("id=\"$it\"" in form, it)
        }
        assertTrue("value=\"-41\"" in form && "value=\"2025\"" in form && "value=\"Year 5 reading\"" in form)
        assertTrue("""${NaplanIds.CODE_ATTRIBUTE}="1"""" in form)
        assertTrue("""href="https://www.myschool.edu.au/"""" in form)
        assertTrue("""aria-expanded="true"""" in out)
    }

    @Test
    fun `the score box asks for a signed difference and lets a minus sign be typed`() {
        val out = suburbHtml(scored = false, open = "naplan-edit-2")
        assertTrue(NAPLAN_SCORE_LABEL in out)
        assertEquals("Difference from national average (points, may be negative, e.g. -41)", NAPLAN_SCORE_LABEL)
        val box = Regex("<input[^>]*id=\"${NaplanIds.SCORE}\"[^>]*>").find(out)!!.value
        assertTrue("type=\"text\"" in box && "autocomplete=\"off\"" in box && "placeholder=\"-41\"" in box, box)
        assertFalse("inputmode" in box, "a numeric keypad can lack a minus key: $box")
        assertFalse("0 to 1000" in out)
    }

    @Test
    fun `a positive figure is shown and filled in with its plus sign`() {
        val out = createHTML().div {
            suburbCard(SuburbCardState.Loaded(suburb(scored = false).let { r -> r.copy(zonedSchools = r.zonedSchools.copy(value = r.zonedSchools.value!!.copy(zones = r.zonedSchools.value!!.zones.map { it.copy(school = it.school.copy(naplan = score.copy(score = 25))) }))) }), groupOpen = mapOf("Schools" to true), naplanOpen = "naplan-edit-2")
        }
        assertTrue("NAPLAN +25 vs national (2025)" in out)
        assertTrue("value=\"+25\"" in out)
    }

    @Test
    fun `with nothing saved the form offers no Clear`() {
        val out = suburbHtml(scored = false, open = "naplan-edit-2")
        assertTrue("id=\"${NaplanIds.CANCEL}\"" in out)
        assertFalse("id=\"${NaplanIds.CLEAR}\"" in out)
        assertFalse("value=\"" in out.substringAfter(NaplanIds.FORM).substringBefore("</form>").substringAfter("Score"), "boxes start empty")
    }

    private val lot = LotReport("1001", null, "9001", "HAMILTON", "2303", listOf("12 MAIN STREET"), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u()).copy(
        schools = Attribute(
            SchoolInfo(
                zoned = listOf(school("1", "Hamilton Public School", score).copy(levelLabel = "primary, K-6")),
                nearest = listOf(NearbySchool(school("2", "Newcastle High School", level = SchoolLevel.HIGH), 900)), zonesChecked = true,
            ),
            Confidence.MEDIUM, "Department of Education",
        ),
    )

    @Test
    fun `the lot card shows the figure in its Schools row and lists each school with its link`() {
        val row = LotCardRows.of(lot).single { it.label == SuburbCardRows.SCHOOLS_LABEL }
        assertTrue("Hamilton Public School (primary, K-6), ICSEA 1,051, NAPLAN -41 vs national (2025)" in row.value!!, row.value)
        assertTrue("Your own figure, typed in." in row.source)
        val out = createHTML().div { lotCard(LotCardState.Loaded(lot), groupOpen = mapOf("Schools" to true), naplanOpen = "naplan-edit-2") }
        assertEquals(listOf("naplan-edit-1", "naplan-edit-2"), ids(out))
        assertTrue("naplan-schools" in out)
        assertTrue(out.indexOf(NaplanIds.FORM) > out.indexOf("naplan-edit-2"))
    }

    @Test
    fun `a school code is made safe for an id`() {
        assertEquals("naplan-edit-12_34", NaplanIds.edit("12\"34"))
    }
}
