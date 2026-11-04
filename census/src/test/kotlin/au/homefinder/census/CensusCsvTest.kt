package au.homefinder.census

import kotlin.test.Test
import kotlin.test.assertEquals

class CensusCsvTest {
    private val dwellings = TenureDwellings("SAL10003", 2233, 825, 651, 371, 1237, 14)

    @Test
    fun `named suburbs are written with quoted names and the others are counted as dropped`() {
        val (lines, dropped) = CensusCsv.format(
            listOf(dwellings, TenureDwellings("SAL99999", 10, 1, 5, 0, 0, 0)),
            mapOf("SAL10003" to "Abbotsford (NSW)"),
        )
        assertEquals(listOf(CensusCsv.HEADER, "SAL10003,\"Abbotsford (NSW)\",2233,825,651,371,1237,14"), lines)
        assertEquals(1, dropped)
    }

    @Test
    fun `a quote inside a name is doubled`() {
        val (lines, _) = CensusCsv.format(listOf(dwellings), mapOf("SAL10003" to "Say \"hi\""))
        assertEquals("SAL10003,\"Say \"\"hi\"\"\",2233,825,651,371,1237,14", lines[1])
    }
}
