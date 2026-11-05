package au.homefinder.census

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class SeifaTest {
    @TempDir
    lateinit var dir: Path

    private val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    private fun workbook(strings: List<String>, vararg sheets: String): Path {
        val file = dir.resolve("seifa.xlsx")
        ZipOutputStream(Files.newOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write("""<?xml version="1.0"?><sst xmlns="$ns">${strings.joinToString("") { "<si><t>$it</t></si>" }}</sst>""".toByteArray())
            zip.closeEntry()
            sheets.forEachIndexed { i, body ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet${i + 1}.xml"))
                zip.write("""<?xml version="1.0"?><worksheet xmlns="$ns"><sheetData>$body</sheetData></worksheet>""".toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    /** Shared strings 0..10: the table's header cells, then names. */
    private val strings = listOf(
        "2021 Suburbs and Localities (SAL) Code", "2021 Suburbs and Localities (SAL) Name", "Score", "Decile", "Usual Resident Population",
        "Hamilton (NSW)", "Tiny Place", "Belmont (Newcastle - NSW)",
    )

    private fun cell(ref: String, value: Int, shared: Boolean = false) = if (shared) """<c r="$ref" t="s"><v>$value</v></c>""" else """<c r="$ref"><v>$value</v></c>"""
    private fun num(ref: String, value: String) = """<c r="$ref"><v>$value</v></c>"""

    private val summary = buildString {
        append("""<row r="1">${cell("A1", 0, true)}${cell("B1", 1, true)}${cell("C1", 2, true)}${cell("D1", 3, true)}${cell("E1", 2, true)}${cell("F1", 3, true)}${cell("K1", 4, true)}</row>""")
        // Hamilton: IRSAD 1,012.5 decile 7, population 5,000.
        append("""<row r="2">${num("A2", "10001")}${cell("B2", 5, true)}${num("C2", "990")}${num("D2", "6")}${num("E2", "1012.5")}${num("F2", "7")}${num("K2", "5000")}</row>""")
        // No scores (an excluded area).
        append("""<row r="3">${num("A3", "10002")}${cell("B3", 6, true)}${num("K3", "4")}</row>""")
        append("""<row r="4">${num("A4", "10003")}${cell("B4", 7, true)}${num("E4", "1100")}${num("F4", "10")}${num("K4", "2100")}</row>""")
        // Victoria (state digit 2): left out.
        append("""<row r="6">${num("A6", "21129")}${cell("B6", 5, true)}${num("E6", "944")}${num("F6", "3")}${num("K6", "10346")}</row>""")
        append("""<row r="5">${cell("A5", 4, true)}</row>""")
    }

    /** A later table: the population heading sits in column C there, so it must not be taken for the summary. */
    private val laterTable = """<row r="1">${cell("A1", 0, true)}${cell("B1", 1, true)}${cell("C1", 4, true)}${cell("D1", 2, true)}</row>""" +
        """<row r="2">${num("A2", "10001")}${cell("B2", 5, true)}${num("C2", "5000")}${num("D2", "999")}</row>"""

    @Test
    fun `reads the IRSAD score, decile and population from the summary table and skips areas with no score`() {
        val result = assertIs<SeifaResult.Read>(Seifa.read(workbook(strings, laterTable, summary)))
        assertEquals(
            listOf(SeifaSuburb("SAL10001", "Hamilton (NSW)", 1012.5, 7, 5000), SeifaSuburb("SAL10003", "Belmont (Newcastle - NSW)", 1100.0, 10, 2100)),
            result.suburbs,
        )
        assertEquals(1, result.withoutScore)
    }

    @Test
    fun `a workbook with no summary table is refused with a reason`() {
        val failed = assertIs<SeifaResult.Failed>(Seifa.read(workbook(strings, laterTable)))
        assertTrue("summary table" in failed.reason, failed.reason)
    }

    @Test
    fun `a file that is not a workbook is refused`() {
        val file = dir.resolve("not.xlsx")
        Files.writeString(file, "not a zip")
        assertIs<SeifaResult.Failed>(Seifa.read(file))
    }

    @Test
    fun `the CSV quotes every name`() {
        assertEquals(
            listOf(SeifaCsv.HEADER, "SAL10001,\"Say \"\"hi\"\", Hamilton\",1012.5,7,5000"),
            SeifaCsv.format(listOf(SeifaSuburb("SAL10001", "Say \"hi\", Hamilton", 1012.5, 7, 5000))),
        )
    }
}
