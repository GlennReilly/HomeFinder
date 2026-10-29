package au.homefinder.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValueRulesTest {
    @Test
    fun `slope classes follow the spec thresholds`() {
        val expected = listOf(
            0.0 to SlopeClass.FLAT, 4.99 to SlopeClass.FLAT, 5.0 to SlopeClass.GENTLE, 9.99 to SlopeClass.GENTLE,
            10.0 to SlopeClass.MODERATE, 20.0 to SlopeClass.MODERATE, 20.01 to SlopeClass.STEEP, 45.0 to SlopeClass.STEEP,
        )
        expected.forEach { (percent, slopeClass) -> assertEquals(slopeClass, SlopeClass.fromMeanPercent(percent), "at $percent%") }
    }

    @Test
    fun `negative slope is rejected`() {
        assertFailsWith<IllegalArgumentException> { SlopeClass.fromMeanPercent(-0.1) }
        assertFailsWith<IllegalArgumentException> { SlopeSummary(-1.0, 2.0, SlopeClass.FLAT, FallDirection.FLAT) }
    }

    @Test
    fun `a maximum slope below the mean is rejected`() {
        assertFailsWith<IllegalArgumentException> { SlopeSummary(8.0, 5.0, SlopeClass.GENTLE, FallDirection.TO_SIDE) }
    }

    @Test
    fun `north facing means within 45 degrees of north`() {
        listOf(0.0, 10.0, 45.0, 315.0, 359.9).forEach { assertTrue(AspectResult.isNorthFacing(it), "$it") }
        listOf(45.01, 90.0, 180.0, 270.0, 314.99).forEach { assertFalse(AspectResult.isNorthFacing(it), "$it") }
    }

    @Test
    fun `the aspect factory sets the north facing flag`() {
        assertTrue(AspectResult.of(350.0, LotShape.REGULAR).isNorthFacing)
        assertFalse(AspectResult.of(180.0, LotShape.REGULAR).isNorthFacing)
    }

    @Test
    fun `only regular lots give an unambiguous aspect`() {
        assertFalse(AspectResult.of(0.0, LotShape.REGULAR).isAmbiguous)
        listOf(LotShape.CORNER, LotShape.BATTLE_AXE, LotShape.IRREGULAR).forEach {
            assertTrue(AspectResult.of(0.0, it).isAmbiguous, "$it")
        }
    }

    @Test
    fun `a bearing outside 0 to 360 is rejected`() {
        assertFailsWith<IllegalArgumentException> { AspectResult.of(360.0, LotShape.REGULAR) }
        assertFailsWith<IllegalArgumentException> { AspectResult.of(-1.0, LotShape.REGULAR) }
    }

    @Test
    fun `a price range must be ordered`() {
        assertFailsWith<IllegalArgumentException> { PriceRange(900_000, 800_000, 1_000_000) }
        assertFailsWith<IllegalArgumentException> { PriceRange(800_000, 900_000, 850_000) }
        assertFailsWith<IllegalArgumentException> { PriceRange(-1, 0, 0) }
        PriceRange(800_000, 800_000, 800_000)
    }

    @Test
    fun `the sample size is the number of comparable sales`() {
        assertEquals(2, SampleReports.fullProperty.estimatedPriceAud.value!!.sampleSize)
        assertEquals(0, PriceRange(1, 2, 3).sampleSize)
    }

    @Test
    fun `block size percentiles must be ordered`() {
        assertFailsWith<IllegalArgumentException> { BlockSizeSpread(medianM2 = 500.0, p10M2 = 600.0, p90M2 = 800.0) }
        assertFailsWith<IllegalArgumentException> { BlockSizeSpread(medianM2 = 500.0, p10M2 = 300.0, p90M2 = 400.0) }
    }

    @Test
    fun `suburb shares must be fractions`() {
        assertFailsWith<IllegalArgumentException> { SuburbFloodShare(1.2, 0.5) }
        assertFailsWith<IllegalArgumentException> { SuburbFloodShare(0.2, -0.1) }
        assertFailsWith<IllegalArgumentException> { SuburbBrickShare(0.5, 1.5) }
        SuburbBrickShare(0.0, 1.0)
    }
}
