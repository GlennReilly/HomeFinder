package au.homefinder.drainage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LotCsvTest {
    @Test
    fun `lot points are read after the header and blank lines are skipped`() {
        val parsed = assertIs<PointsResult.Parsed>(
            LotCsv.parsePoints(listOf("lot_id,longitude,latitude", "100020101,151.675,-32.9203", "", "7//DP9,151.7,-32.9")),
        )
        assertEquals(listOf(LotPoint("100020101", 151.675, -32.9203), LotPoint("7//DP9", 151.7, -32.9)), parsed.points)
    }

    @Test
    fun `a wrong header is refused`() {
        val malformed = assertIs<PointsResult.Malformed>(LotCsv.parsePoints(listOf("id,x,y", "1,2,3")))
        assertEquals(1, malformed.lineNumber)
    }

    @Test
    fun `a missing file content is refused`() {
        assertIs<PointsResult.Malformed>(LotCsv.parsePoints(emptyList()))
    }

    @Test
    fun `a row with the wrong number of fields is refused with its line number`() {
        val malformed = assertIs<PointsResult.Malformed>(LotCsv.parsePoints(listOf("lot_id,longitude,latitude", "1,151.0,-33.0", "2,151.0")))
        assertEquals(3, malformed.lineNumber)
    }

    @Test
    fun `a row with a coordinate that is not a number is refused`() {
        val malformed = assertIs<PointsResult.Malformed>(LotCsv.parsePoints(listOf("lot_id,longitude,latitude", "1,east,-33.0")))
        assertEquals(2, malformed.lineNumber)
    }

    @Test
    fun `results are written with two decimals and a point whatever the locale`() {
        val lines = LotCsv.formatResults(listOf(LotDrainage("1", 0.4567, 21.0), LotDrainage("2", 12.0, 135.949)))
        assertEquals(listOf("lot_id,height_above_drainage_m,stream_catchment_ha", "1,0.46,21.00", "2,12.00,135.95"), lines)
    }
}
