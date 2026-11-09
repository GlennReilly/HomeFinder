package au.homefinder.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZonedNaplanTest {
    private fun zone(code: Int, level: String, share: Double, score: Int?) = ZoneNaplanShare(code, level, share, score)

    @Test
    fun `the average is weighted by each zone's share of the suburb`() {
        // (0.8 * 30 + 0.2 * -20) / 1.0 = 20
        assertEquals(20, ZonedNaplan.average(listOf(zone(1, "PRIMARY", 0.8, 30), zone(2, "HIGH", 0.2, -20))))
    }

    @Test
    fun `a zoned school with no figure is left out rather than counted as zero`() {
        assertEquals(30, ZonedNaplan.average(listOf(zone(1, "PRIMARY", 0.8, 30), zone(2, "HIGH", 0.9, null))))
    }

    @Test
    fun `a suburb where no zoned school has a figure has no value`() {
        assertNull(ZonedNaplan.average(listOf(zone(1, "PRIMARY", 0.8, null), zone(2, "HIGH", 0.9, null))))
        assertNull(ZonedNaplan.average(emptyList()))
    }

    @Test
    fun `all-negative figures give a negative average`() {
        // (0.5 * -40 + 0.5 * -10) / 1.0 = -25
        assertEquals(-25, ZonedNaplan.average(listOf(zone(1, "PRIMARY", 0.5, -40), zone(2, "HIGH", 0.5, -10))))
    }

    @Test
    fun `mixed signs average across zero, and halves round upwards`() {
        // (0.6 * 50 + 0.6 * -50) / 1.2 = 0
        assertEquals(0, ZonedNaplan.average(listOf(zone(1, "PRIMARY", 0.6, 50), zone(2, "HIGH", 0.6, -50))))
        // (0.5 * 3 + 0.5 * -8) / 1.0 = -2.5, which rounds up to -2
        assertEquals(-2, ZonedNaplan.average(listOf(zone(1, "PRIMARY", 0.5, 3), zone(2, "HIGH", 0.5, -8))))
        // (0.5 * 2 + 0.5 * 3) / 1.0 = 2.5, which rounds up to 3
        assertEquals(3, ZonedNaplan.average(listOf(zone(1, "PRIMARY", 0.5, 2), zone(2, "HIGH", 0.5, 3))))
    }

    @Test
    fun `only the zones the Suburb Card lists count, so a small zone's figure is ignored when a level has a big one`() {
        // The 10% primary zone is not listed (the 70% one is), so its +500 does not count.
        assertEquals(-10, ZonedNaplan.average(listOf(zone(1, "PRIMARY", 0.7, -10), zone(2, "PRIMARY", 0.1, 500))))
        // With no zone over a quarter, each level's biggest zone is listed, as on the card.
        assertEquals(40, ZonedNaplan.average(listOf(zone(1, "PRIMARY", 0.2, 40), zone(2, "PRIMARY", 0.15, -90))))
        // A listed zone with no figure does not let an unlisted one stand in for it.
        assertNull(ZonedNaplan.average(listOf(zone(1, "PRIMARY", 0.7, null), zone(2, "PRIMARY", 0.1, 500))))
    }

    @Test
    fun `every suburb gets its own average and a suburb with none is left out`() {
        val values = ZonedNaplan.bySuburb(
            mapOf(
                "9001" to listOf(zone(1, "PRIMARY", 0.8, 30), zone(2, "HIGH", 0.8, 10)),
                "9002" to listOf(zone(2, "HIGH", 1.0, 10)),
                "9003" to listOf(zone(3, "PRIMARY", 1.0, null)),
            ),
        )
        assertEquals(mapOf("9001" to 20, "9002" to 10), values)
    }
}
