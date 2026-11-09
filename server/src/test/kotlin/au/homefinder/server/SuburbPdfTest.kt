package au.homefinder.server

import au.homefinder.app.selection.CardLayout
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuburbPdfTest {
    private fun document(count: Int, filters: List<String> = listOf("Homes rented: At most 45%")) = ExportDocument(
        date = "2026-11-09",
        filters = filters,
        suburbs = (1..count).map { SuburbExportContent.suburb(ExportSamples.report("90$it", "SUBURB $it", rated = it % 2 == 0), CardLayout()) },
    )

    private fun pages(bytes: ByteArray): Int = Loader.loadPDF(bytes).use { it.numberOfPages }

    private fun text(bytes: ByteArray): String = Loader.loadPDF(bytes).use { PDFTextStripper().getText(it) }

    @Test
    fun `it makes a PDF with a cover and one section per suburb`() {
        val bytes = SuburbPdf.render(document(3))
        assertEquals("%PDF", bytes.copyOfRange(0, 4).decodeToString())
        assertTrue(pages(bytes) >= 1 + 3, "a cover and at least a page per suburb")
        val text = text(bytes)
        assertTrue(SuburbPdf.TITLE in text, text)
        assertTrue("Made on 2026-11-09 · 3 suburbs" in text, text)
        assertTrue("- Homes rented: At most 45%" in text, text)
        assertTrue("SUBURB 2" in text && "Postcode 2303" in text && "Prices and blocks" in text && "$900,000" in text, text)
        assertTrue("NAPLAN +5 vs national" in text, text)
        assertTrue("4.1 * (7 reviews)" in text, text)
        println("PDF for 3 suburbs: ${bytes.size} bytes")
    }

    @Test
    fun `the page count grows with the suburbs`() {
        assertTrue(pages(SuburbPdf.render(document(5))) > pages(SuburbPdf.render(document(2))))
    }

    @Test
    fun `characters Helvetica cannot draw are replaced instead of failing`() {
        val odd = "Stars ★ diamonds ◆ ≥ 5 – en dash, emoji 😀, Chinese 中文, a tab\tand a nul\u0000 and ² m² · ×"
        val bytes = SuburbPdf.render(document(1, listOf(odd, "x".repeat(2_000), "")))
        val text = text(bytes)
        for (part in listOf("Stars * diamonds * >= 5", "– en dash", "emoji ?,", "Chinese ??", "a tab and a nul and", "m² · ×")) assertTrue(part in text, "$part in $text")
    }

    @Test
    fun `an export with no filter lines says so`() {
        assertTrue(SuburbPdf.NO_FILTERS_TEXT in text(SuburbPdf.render(document(1, emptyList()))))
    }

    @Test
    fun `a suburb with very long values wraps and runs onto more pages without failing`() {
        val long = ExportSuburb("LONG", null, listOf(ExportGroup("Group", (1..60).map { ExportLine("Row $it with a fairly long label that wraps", "value ".repeat(40), listOf("  sub line"), "source") })))
        val bytes = SuburbPdf.render(ExportDocument("2026-11-09", emptyList(), listOf(long)))
        assertTrue(pages(bytes) > 2)
    }
}
