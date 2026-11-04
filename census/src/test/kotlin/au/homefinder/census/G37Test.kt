package au.homefinder.census

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class G37Test {
    private val header = "SAL_CODE_2021,R_Tot_Total,Total_Total,Ten_type_NS_Total,Total_DS_Sep_house,Total_DS_SemiD_ro_or_tce_h_th,Total_DS_Flat_apart,Total_DS_Oth_dwell,Total_DS_not_stated"

    private fun parsed(vararg rows: String): List<TenureDwellings> =
        assertIs<G37Result.Parsed>(G37.parse(listOf(header) + rows)).suburbs

    @Test
    fun `a row gives the rented count, the tenure base and the dwelling mix`() {
        val suburb = parsed("SAL10003,825,2267,34,651,371,1237,14,0").single()
        assertEquals("SAL10003", suburb.salCode)
        assertEquals(825, suburb.rented)
        assertEquals(2233, suburb.tenureStated, "all dwellings less those whose tenure was not stated")
        assertEquals(listOf(651, 371, 1237, 14), listOf(suburb.houses, suburb.semis, suburb.flats, suburb.otherDwellings))
        assertEquals(2273, suburb.structureStated, "the four structures added up")
    }

    @Test
    fun `the ABS's random adjustment of small counts never makes the rented share exceed all dwellings`() {
        // Rented 14 but only 12 dwellings with a stated tenure: the base is raised to the rented count.
        val suburb = parsed("SAL1,14,14,2,9,0,0,0,0").single()
        assertEquals(14, suburb.tenureStated)
        assertEquals(14, suburb.rented)
    }

    @Test
    fun `columns are found by name so their order does not matter`() {
        val shuffled = "Total_DS_Flat_apart,SAL_CODE_2021,Total_Total,R_Tot_Total,Ten_type_NS_Total,Total_DS_Sep_house,Total_DS_SemiD_ro_or_tce_h_th,Total_DS_Oth_dwell,Total_DS_not_stated"
        val suburb = assertIs<G37Result.Parsed>(G37.parse(listOf(shuffled, "7,SAL9,100,40,5,50,30,3,0"))).suburbs.single()
        assertEquals(listOf("SAL9", 40, 95, 7), listOf(suburb.salCode, suburb.rented, suburb.tenureStated, suburb.flats))
    }

    @Test
    fun `blank lines are skipped`() {
        assertEquals(2, parsed("SAL1,0,10,0,10,0,0,0,0", "", "SAL2,5,10,0,5,5,0,0,0").size)
    }

    @Test
    fun `a missing column, a short row or a value that is not a count is refused with its line`() {
        assertEquals(1, assertIs<G37Result.Malformed>(G37.parse(listOf("SAL_CODE_2021,R_Tot_Total", "SAL1,5"))).lineNumber)
        assertEquals(2, assertIs<G37Result.Malformed>(G37.parse(listOf(header, "SAL1,5,10"))).lineNumber)
        assertEquals(3, assertIs<G37Result.Malformed>(G37.parse(listOf(header, "SAL1,0,10,0,10,0,0,0,0", "SAL2,x,10,0,5,5,0,0,0"))).lineNumber)
        assertEquals(2, assertIs<G37Result.Malformed>(G37.parse(listOf(header, "SAL1,-1,10,0,10,0,0,0,0"))).lineNumber)
        assertIs<G37Result.Malformed>(G37.parse(emptyList()))
    }

    @Test
    fun `counts that cannot be negative are refused by the model`() {
        assertFailsWith<IllegalArgumentException> { TenureDwellings("S", 5, 6, 1, 0, 0, 0) }
        assertFailsWith<IllegalArgumentException> { TenureDwellings("S", 5, 1, -1, 0, 0, 0) }
    }
}
