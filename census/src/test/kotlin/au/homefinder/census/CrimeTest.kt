package au.homefinder.census

import java.io.BufferedReader
import java.io.StringReader
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrimeTest {
    /** 26 month columns, Mar 2024 to Apr 2026: only the last 24 count (May 2024 to Apr 2026). */
    private val months = generateSequence(YearMonth.of(2024, 3)) { it.plusMonths(1) }.take(26).toList()

    private fun monthLabel(m: YearMonth) = m.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) + " " + m.year

    private fun header() = (listOf("Suburb", "Offence category", "Subcategory") + months.map(::monthLabel)).joinToString(",") { "\"$it\"" }

    /** A row whose first two months are [early], and every later month [each]. */
    private fun row(suburb: String, category: String, sub: String, early: Int, each: Int) =
        (listOf(suburb, category, sub) + List(2) { early.toString() } + List(24) { each.toString() }).joinToString(",") { "\"$it\"" }

    private fun read(vararg lines: String) = Crime.read(BufferedReader(StringReader((listOf(header()) + lines).joinToString("\n"))))

    @Test
    fun `the latest 24 months of the violent and property subcategories are summed per suburb`() {
        val result = assertIs<CrimeResult.Read>(
            read(
                row("Hamilton", "Assault", "Domestic violence related assault", early = 50, each = 1),
                row("Hamilton", "Robbery", "Robbery without a weapon", early = 50, each = 0),
                row("Hamilton", "Theft", "Steal from motor vehicle", early = 50, each = 2),
                row("Hamilton", "Theft", "Fraud", early = 50, each = 9),
                row("Hamilton", "Homicide", "Murder *", early = 5, each = 5),
                row("Wallsend", "Theft", "Break and enter dwelling", early = 0, each = 1),
            ),
        )
        assertEquals(listOf(SuburbCrime("Hamilton", violent = 24, property = 48), SuburbCrime("Wallsend", violent = 0, property = 24)), result.suburbs)
        assertEquals(YearMonth.of(2024, 5), result.firstMonth)
        assertEquals(YearMonth.of(2026, 4), result.lastMonth)
        assertEquals("May 2024 to Apr 2026", result.windowText)
        assertEquals(LocalDate.of(2026, 4, 30), result.asOf)
    }

    @Test
    fun `a subcategory name with a comma stays in one cell`() {
        assertEquals(listOf("a", "b, c", "say \"hi\""), Crime.parseLine("\"a\",\"b, c\",\"say \"\"hi\"\"\""))
        val result = assertIs<CrimeResult.Read>(read(row("Hamilton", "Sexual offences", "Sexual touching, sexual act and other sexual offences", early = 0, each = 1)))
        assertEquals(24, result.suburbs.single().violent)
    }

    @Test
    fun `a file that is not the BOCSAR layout is refused with a reason`() {
        assertIs<CrimeResult.Failed>(Crime.read(BufferedReader(StringReader("a,b,c\n1,2,3\n"))))
        assertIs<CrimeResult.Failed>(Crime.read(BufferedReader(StringReader(""))))
        val badMonth = "\"Suburb\",\"Offence category\",\"Subcategory\"," + (1..24).joinToString(",") { "\"Foo $it\"" }
        assertTrue("Foo" in assertIs<CrimeResult.Failed>(Crime.read(BufferedReader(StringReader(badMonth)))).reason)
        val short = assertIs<CrimeResult.Failed>(read("\"Hamilton\",\"Assault\""))
        assertTrue("cells" in short.reason, short.reason)
    }

    @Test
    fun `month labels are read, and others are not`() {
        assertEquals(YearMonth.of(2026, 6), Crime.parseMonth("Jun 2026"))
        assertNull(Crime.parseMonth("June"))
        assertNull(Crime.parseMonth("Foo 2026"))
    }

    @Test
    fun `the CSV quotes names and carries the window`() {
        val result = assertIs<CrimeResult.Read>(read(row("Hamilton, East", "Assault", "Assault Police", early = 0, each = 1)))
        assertEquals(listOf(CrimeCsv.HEADER, "\"Hamilton, East\",24,0,\"May 2024 to Apr 2026\",2026-04-30"), CrimeCsv.format(result))
    }
}
