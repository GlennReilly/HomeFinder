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
import au.homefinder.shared.model.SuburbZone
import au.homefinder.shared.model.SuburbZonedSchools
import kotlinx.datetime.LocalDate
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The HTML with the pretty-printing whitespace between tags removed. */
private fun flat(html: String): String = html.replace(Regex(">\\s+<"), "><")

class NaplanHtmlTest {
    private fun <T> u() = Attribute.unknown<T>("src", note = "Not computed yet")
    private val score = NaplanScore(-41, LocalDate(2026, 11, 8))
    private fun school(id: String, name: String, naplan: NaplanScore? = null, level: SchoolLevel = SchoolLevel.PRIMARY) =
        School(id, name, SchoolSector.PUBLIC, level, 1051, naplan = naplan)

    private fun suburb(scored: Boolean) = SuburbReport(
        "9001", "HAMILTON", "2303", Attribute(1.0, Confidence.HIGH, "src"), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(), u(),
    ).copy(
        zonedSchools = Attribute(
            SuburbZonedSchools(listOf(SuburbZone(school("1", "Hamilton Public School", score.takeIf { scored }), 0.8), SuburbZone(school("2", "Newcastle High School", level = SchoolLevel.HIGH), 0.7))),
            Confidence.MEDIUM, "catchments",
        ),
    )

    private fun suburbHtml(scored: Boolean, open: String? = null) =
        createHTML().div { suburbCard(SuburbCardState.Loaded(suburb(scored)), groupOpen = mapOf("Schools" to true), naplanOpen = open) }

    private fun ids(out: String) = Regex("""id="(naplan-edit-[^"]*)"""").findAll(out).map { it.groupValues[1] }.toList()

    @Test
    fun `every school listed gets its own Add NAPLAN link`() {
        val out = suburbHtml(scored = false)
        assertEquals(listOf("naplan-edit-1", "naplan-edit-2"), ids(out))
        assertEquals(2, Regex(">$NAPLAN_ADD_TEXT<").findAll(out).count())
        assertFalse("vs national" in out)
        assertFalse(NaplanIds.FORM in out, "no form until a link is pressed")
        assertFalse("Your own figure" in out, "the Source wording goes with a figure")
    }

    @Test
    fun `a saved figure has its own line under the ICSEA in the Schools row, the button says Edit and the Source says it is your own figure`() {
        val out = suburbHtml(scored = true)
        val figure = """<div class="school-icsea">ICSEA 1,051</div><div class="naplan-line"><span class="naplan-figure">NAPLAN -41 vs national</span><button"""
        assertTrue(figure in flat(out), out)
        assertEquals(1, Regex(">$NAPLAN_EDIT_TEXT<").findAll(out).count())
        assertTrue("catchments; Your own figure, typed in." in out)
        assertFalse("Source: Australian Curriculum" in out, "the app does not say the figure came from ACARA")
        // The second school has no figure: still just the Add link.
        assertTrue("aria-label=\"$NAPLAN_ADD_TEXT for Newcastle High School\"" in out)
    }

    @Test
    fun `the open form sits under the school whose link was pressed, filled with the saved figure`() {
        val out = suburbHtml(scored = true, open = "naplan-edit-1")
        assertEquals(1, Regex("""id="${NaplanIds.FORM}"""").findAll(out).count())
        val form = out.substringAfter("""id="naplan-edit-1"""")
        assertTrue(form.indexOf(NaplanIds.FORM) < form.indexOf("naplan-edit-2"), "the form comes before the next school")
        listOf(NaplanIds.SCORE, NaplanIds.SAVE, NaplanIds.CLEAR, NaplanIds.CANCEL, NaplanIds.MESSAGE).forEach {
            assertTrue("id=\"$it\"" in form, it)
        }
        assertTrue("value=\"-41\"" in form)
        assertTrue("""${NaplanIds.CODE_ATTRIBUTE}="1"""" in form)
        assertFalse("naplan-note" in out || "Note (optional)" in out, "the note is gone (ADR 0071)")
        assertFalse("myschool.edu.au" in form, "no My School link in the form")
        assertTrue("""aria-expanded="true"""" in out)
    }

    @Test
    fun `the score box asks for a signed difference and lets a minus sign be typed`() {
        val out = suburbHtml(scored = false, open = "naplan-edit-2")
        assertTrue(NAPLAN_SCORE_LABEL in out)
        assertEquals("Difference from national average (points, may be negative)", NAPLAN_SCORE_LABEL)
        val box = Regex("<input[^>]*id=\"${NaplanIds.SCORE}\"[^>]*>").find(out)!!.value
        assertTrue("type=\"text\"" in box && "autocomplete=\"off\"" in box && "placeholder=\"-5\"" in box, box)
        assertFalse("inputmode" in box, "a numeric keypad can lack a minus key: $box")
        assertFalse("0 to 1000" in out)
    }

    @Test
    fun `the form has no My School link or fetch note, and opens above the school's links`() {
        val school = School("7", "Plain School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, icsea = 990, website = "https://plain.schools.nsw.gov.au", locality = "Plain")
        val out = createHTML().div { schoolEntry(school, null, NaplanSlots("naplan-edit-7")) }
        assertFalse("myschool.edu.au" in out || "Nothing is fetched" in out || "for your own use" in out)
        assertTrue(out.indexOf(NaplanIds.FORM) in 0 until out.indexOf(SchoolLines.LINKS_LINE), "the form comes before the links")
    }

    @Test
    fun `a positive figure is shown and filled in with its plus sign`() {
        val out = createHTML().div {
            suburbCard(SuburbCardState.Loaded(suburb(scored = false).let { r -> r.copy(zonedSchools = r.zonedSchools.copy(value = r.zonedSchools.value!!.copy(zones = r.zonedSchools.value!!.zones.map { it.copy(school = it.school.copy(naplan = score.copy(score = 25))) }))) }), groupOpen = mapOf("Schools" to true), naplanOpen = "naplan-edit-2")
        }
        assertTrue("NAPLAN +25 vs national" in out)
        assertTrue("value=\"+25\"" in out)
    }

    @Test
    fun `the form has no year box and the figure shows no year (ADR 0073)`() {
        val out = suburbHtml(scored = true, open = "naplan-edit-1")
        assertFalse("naplan-year" in out || "Year" in out.substringAfter(NaplanIds.FORM), out)
        assertTrue("""<span class="naplan-figure">NAPLAN -41 vs national</span>""" in out, out)
    }

    @Test
    fun `each school is drawn line by line, leaving out the ICSEA line when there is none`() {
        fun lines(school: School): List<String> {
            val out = createHTML().div { schoolEntry(school, "Primary School", NaplanSlots(null)) }
            return Regex("""<div class="([^"]+)">""").findAll(out).map { it.groupValues[1] }.toList()
        }
        val base = School("7", "Plain School", SchoolSector.PUBLIC, SchoolLevel.PRIMARY, icsea = null)
        assertEquals(listOf(SchoolLines.NAME_LINE, SchoolLines.NAPLAN_LINE), lines(base), "no ICSEA, no figure: name, then the Add button alone")
        assertEquals(listOf(SchoolLines.NAME_LINE, SchoolLines.ICSEA_LINE, SchoolLines.NAPLAN_LINE), lines(base.copy(icsea = 990)))
        assertEquals(
            listOf(SchoolLines.NAME_LINE, SchoolLines.ICSEA_LINE, SchoolLines.NAPLAN_LINE, SchoolLines.LINKS_LINE),
            lines(base.copy(icsea = 990, naplan = score, website = "https://plain.schools.nsw.gov.au", locality = "Plain")),
        )
        val withFigure = flat(createHTML().div { schoolEntry(base.copy(naplan = score), null, NaplanSlots(null)) })
        assertTrue(Regex("""<div class="naplan-line"><span class="naplan-figure">NAPLAN -41 vs national</span><button[^>]*>$NAPLAN_EDIT_TEXT</button></div>""").containsMatchIn(withFigure), withFigure)
        val noFigure = flat(createHTML().div { schoolEntry(base, null, NaplanSlots(null)) })
        assertTrue(Regex("""<div class="naplan-line"><button[^>]*>$NAPLAN_ADD_TEXT</button></div>""").containsMatchIn(noFigure), noFigure)
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
        assertEquals("Zoned: Hamilton Public School (primary, K-6); Nearest, not zoned: Newcastle High School, 900 m away", row.value, "the row names the schools; ICSEA and NAPLAN have their own lines")
        assertTrue("Your own figure, typed in." in row.source)
        val out = createHTML().div { lotCard(LotCardState.Loaded(lot), groupOpen = mapOf("Schools" to true), naplanOpen = "naplan-edit-2") }
        assertEquals(listOf("naplan-edit-1", "naplan-edit-2"), ids(out))
        assertTrue("naplan-schools" in out)
        assertTrue("""<span class="school-name">Hamilton Public School</span><span class="detail"> · primary, K-6</span></div><div class="school-icsea">ICSEA 1,051</div><div class="naplan-line"><span class="naplan-figure">NAPLAN -41 vs national</span>""" in flat(out), out)
        assertTrue("""<span class="school-name">Newcastle High School</span><span class="detail"> · 900 m away</span>""" in flat(out), out)
        assertTrue(out.indexOf(NaplanIds.FORM) > out.indexOf("naplan-edit-2"))
    }

    @Test
    fun `a school code is made safe for an id`() {
        assertEquals("naplan-edit-12_34", NaplanIds.edit("12\"34"))
    }
}
