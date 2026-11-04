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

class SalNamesTest {
    @TempDir
    lateinit var dir: Path

    /** A minimal workbook: shared strings (one rich text), and a sheet whose cells are sparse like the real file's. */
    private fun workbook(vararg entries: Pair<String, String>): Path {
        val file = dir.resolve("geog.xlsx")
        ZipOutputStream(Files.newOutputStream(file)).use { zip ->
            entries.forEach { (name, body) -> zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry() }
        }
        return file
    }

    private val strings = """<?xml version="1.0"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <si><t>SAL</t></si><si><t>SAL10001</t></si><si><t>Aarons Pass</t></si><si><t>SAL10003</t></si>
        <si><r><t>Abbotsford </t></r><r><t>(NSW)</t></r></si><si><t>CED</t></si><si><t>CED101</t></si><si><t>Banks</t></si></sst>"""

    private val sheet = """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
        <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1"><v>10001</v></c><c r="D1" t="s"><v>2</v></c><c r="E1"><v>12.5</v></c></row>
        <row r="2"><c r="A2" t="s"><v>0</v></c><c r="B2" t="s"><v>3</v></c><c r="D2" t="s"><v>4</v></c></row>
        <row r="3"><c r="A3" t="s"><v>5</v></c><c r="B3" t="s"><v>6</v></c><c r="D3" t="s"><v>7</v></c></row>
        <row r="4"><c r="A4" t="s"><v>0</v></c><c r="B4" t="s"><v>1</v></c></row>
        </sheetData></worksheet>"""

    @Test
    fun `suburb rows give a code and a name, other structures and incomplete rows are left out`() {
        val read = assertIs<SalNamesResult.Read>(SalNames.read(workbook("xl/sharedStrings.xml" to strings, "xl/worksheets/sheet1.xml" to sheet)))
        assertEquals(mapOf("SAL10001" to "Aarons Pass", "SAL10003" to "Abbotsford (NSW)"), read.namesByCode)
    }

    @Test
    fun `a name split over rich text runs is joined`() {
        val read = assertIs<SalNamesResult.Read>(SalNames.read(workbook("xl/sharedStrings.xml" to strings, "xl/worksheets/sheet1.xml" to sheet)))
        assertEquals("Abbotsford (NSW)", read.namesByCode["SAL10003"])
    }

    @Test
    fun `every worksheet is read`() {
        val second = sheet.replace("<v>1</v>", "<v>3</v>").replace("<v>3</v></c><c r=\"C1\">", "<v>3</v></c><c r=\"C1\">")
        val read = assertIs<SalNamesResult.Read>(
            SalNames.read(workbook("xl/sharedStrings.xml" to strings, "xl/worksheets/sheet1.xml" to sheet, "xl/worksheets/sheet2.xml" to second)),
        )
        assertTrue("SAL10003" in read.namesByCode)
    }

    @Test
    fun `a workbook with no suburbs is refused`() {
        val none = sheet.replace("<v>0</v>", "<v>5</v>")
        assertIs<SalNamesResult.Failed>(SalNames.read(workbook("xl/sharedStrings.xml" to strings, "xl/worksheets/sheet1.xml" to none)))
    }

    @Test
    fun `a file that is not a workbook is refused`() {
        Files.writeString(dir.resolve("x.xlsx"), "not a zip")
        assertIs<SalNamesResult.Failed>(SalNames.read(dir.resolve("x.xlsx")))
        assertIs<SalNamesResult.Failed>(SalNames.read(workbook("other.xml" to "<a/>")))
    }

    @Test
    fun `column letters become zero based numbers`() {
        assertEquals(0, SalNames.columnOf("A1"))
        assertEquals(3, SalNames.columnOf("D12"))
        assertEquals(26, SalNames.columnOf("AA7"))
        assertEquals(-1, SalNames.columnOf(null))
    }
}
