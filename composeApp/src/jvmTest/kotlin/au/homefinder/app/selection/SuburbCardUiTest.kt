package au.homefinder.app.selection

import au.homefinder.app.map.MapInteraction
import au.homefinder.app.map.MapTap
import au.homefinder.app.ui.cardSubtitle
import au.homefinder.app.ui.cardTitle
import au.homefinder.app.ui.confidenceLabel
import au.homefinder.app.ui.provenanceLine
import au.homefinder.shared.api.SuburbRef
import au.homefinder.shared.model.Attribute
import au.homefinder.shared.model.Confidence
import au.homefinder.shared.model.SuburbReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SuburbCardUiTest {
    private val ref = SuburbRef("9001", "HAMILTON", "2303")

    private fun report() = SuburbReport(
        "9001", "HAMILTON", null, Attribute(1.0, Confidence.HIGH, "s"), Attribute.unknown("s"), Attribute.unknown("s"), Attribute.unknown("s"),
        Attribute.unknown("s"), Attribute.unknown("s"), Attribute.unknown("s"), Attribute.unknown("s"), Attribute.unknown("s"),
        Attribute.unknown("s"), Attribute.unknown("s"), Attribute.unknown("s"), Attribute.unknown("s"),
    )

    @Test
    fun `the title is the suburb name as soon as it is known`() {
        assertEquals("HAMILTON", cardTitle(SuburbCardState.Loading("9001", ref)))
        assertEquals("Loading suburb…", cardTitle(SuburbCardState.Loading("9001", null)))
        assertEquals("HAMILTON", cardTitle(SuburbCardState.Loaded(report())))
        assertEquals("Suburb", cardTitle(SuburbCardState.Failed("9001", "x")))
        assertEquals("", cardTitle(SuburbCardState.Hidden))
    }

    @Test
    fun `the postcode shows when there is one`() {
        assertEquals("Postcode 2303", cardSubtitle(SuburbCardState.Loading("9001", ref)))
        assertNull(cardSubtitle(SuburbCardState.Loaded(report()))) // this report has no postcode
        assertNull(cardSubtitle(SuburbCardState.Hidden))
    }

    @Test
    fun `provenance names the source and adds date and confidence only when known`() {
        val full = CardRow("Area", "4 km²", "NSW Spatial Services", "2026-10-29", null, Confidence.HIGH)
        assertEquals("Source: NSW Spatial Services · as of 2026-10-29 · high confidence", provenanceLine(full))
        val unknown = CardRow("Flood", null, "Council flood study", null, "Not computed yet", Confidence.NONE)
        assertEquals("Source: Council flood study", provenanceLine(unknown))
    }

    @Test
    fun `every confidence level has a label except none`() {
        assertEquals(listOf("high confidence", "medium confidence", "low confidence", null), Confidence.entries.map(::confidenceLabel))
    }

    @Test
    fun `the default interaction selects nothing and ignores taps`() {
        assertNull(MapInteraction.None.selectedSuburbId())
        MapInteraction.None.onTap(MapTap(-32.9, 151.7, 10.0))
    }

    @Test
    fun `an interaction reads the current selection each time`() {
        var selected: String? = null
        val interaction = MapInteraction(selectedSuburbId = { selected })
        assertNull(interaction.selectedSuburbId())
        selected = "9001"
        assertEquals("9001", interaction.selectedSuburbId())
    }
}
