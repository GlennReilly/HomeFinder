package au.homefinder.app.map

import au.homefinder.shared.model.SuburbFilterValue
import kotlin.test.Test
import kotlin.test.assertEquals

class NaplanLabelsTest {
    private fun value(id: String, naplan: Int?, latitude: Double? = -32.92, longitude: Double? = 151.75) =
        SuburbFilterValue(id, name = "S$id", zonedNaplan = naplan, latitude = latitude, longitude = longitude)

    @Test
    fun `the text shows the sign always, and zero as 0`() {
        assertEquals("+27", NaplanLabels.text(27))
        assertEquals("-41", NaplanLabels.text(-41))
        assertEquals("0", NaplanLabels.text(0))
    }

    @Test
    fun `a suburb with an average gets a label at its name's point, longitude first`() {
        assertEquals(listOf(NaplanLabel("9001", "S9001", "+12", LonLat(151.75, -32.92))), NaplanLabels.from(listOf(value("9001", 12))))
    }

    @Test
    fun `a suburb without a typed figure, or without a point, gets no label`() {
        assertEquals(emptyList(), NaplanLabels.from(listOf(value("9001", null), value("9002", 5, latitude = null), value("9003", 5, longitude = null))))
    }

    @Test
    fun `the server's whole-point average is shown as it is, with no second rounding`() {
        // ADR 0074: the server rounds the share-weighted average (halves upwards); the label shows that value unchanged.
        val labels = NaplanLabels.from(listOf(value("1", -1), value("2", 1000), value("3", -1000), value("4", 0)))
        assertEquals(listOf("-1", "+1000", "-1000", "0"), labels.map { it.text })
    }
}
