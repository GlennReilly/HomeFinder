package au.homefinder.census

import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/** What [SalNames.read] found. */
sealed interface SalNamesResult {
    data class Read(val namesByCode: Map<String, String>) : SalNamesResult
    data class Failed(val reason: String) : SalNamesResult
}

/**
 * The suburb (SAL) code-to-name lookup from the ABS geography workbook (`2021Census_geog_desc_*.xlsx`, ADR 0044). There is no spreadsheet
 * library on the build, and the file is plain zipped XML, so the shared strings and every worksheet are read directly: a row counts when
 * its first cell is `SAL`, its second is the code (`SAL10001`) and its fourth the name.
 */
object SalNames {
    private const val STRUCTURE_COLUMN = 0
    private const val CODE_COLUMN = 1
    private const val NAME_COLUMN = 3
    private const val SAL_STRUCTURE = "SAL"
    private const val SHARED_STRINGS = "xl/sharedStrings.xml"
    private const val SHEET_PREFIX = "xl/worksheets/sheet"
    private const val LETTERS = 26

    fun read(workbook: Path): SalNamesResult = try {
        ZipFile(workbook.toFile()).use { zip ->
            val sharedEntry = zip.getEntry(SHARED_STRINGS) ?: return SalNamesResult.Failed("$workbook has no $SHARED_STRINGS; it is not an xlsx workbook")
            val strings = zip.getInputStream(sharedEntry).use { sharedStrings(it) }
            val names = HashMap<String, String>()
            zip.entries().asSequence().filter { it.name.startsWith(SHEET_PREFIX) && it.name.endsWith(".xml") }.forEach { sheet ->
                zip.getInputStream(sheet).use { rows(it, strings) { row -> collect(row, names) } }
            }
            if (names.isEmpty()) SalNamesResult.Failed("$workbook lists no suburbs (no row starts with $SAL_STRUCTURE)") else SalNamesResult.Read(names)
        }
    } catch (failure: IOException) {
        SalNamesResult.Failed("Could not read $workbook: ${failure.message}")
    } catch (failure: javax.xml.stream.XMLStreamException) {
        SalNamesResult.Failed("Could not parse $workbook: ${failure.message}")
    }

    private fun collect(row: Map<Int, String>, into: MutableMap<String, String>) {
        if (row[STRUCTURE_COLUMN] != SAL_STRUCTURE) return
        val code = row[CODE_COLUMN] ?: return
        val name = row[NAME_COLUMN] ?: return
        into[code] = name
    }

    private fun factory(): XMLInputFactory = XMLInputFactory.newFactory().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_COALESCING, true)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }

    /** Every `<si>` is one string, made of all the `<t>` texts inside it (rich text splits one string over several). */
    internal fun sharedStrings(stream: java.io.InputStream): List<String> {
        val reader = factory().createXMLStreamReader(stream)
        val strings = ArrayList<String>()
        var current: StringBuilder? = null
        var inText = false
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                    "si" -> current = StringBuilder()
                    "t" -> inText = current != null
                }
                XMLStreamConstants.CHARACTERS -> if (inText) current?.append(reader.text)
                XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
                    "t" -> inText = false
                    "si" -> { strings += current?.toString().orEmpty(); current = null }
                }
            }
        }
        return strings
    }

    /** Calls [onRow] with each row's cells by zero-based column; empty cells are absent, as in the file. */
    internal fun rows(stream: java.io.InputStream, strings: List<String>, onRow: (Map<Int, String>) -> Unit) {
        val reader = factory().createXMLStreamReader(stream)
        var row = HashMap<Int, String>()
        var column = -1
        var isShared = false
        var inValue = false
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                    "row" -> row = HashMap()
                    "c" -> { column = columnOf(reader.getAttributeValue(null, "r")); isShared = reader.getAttributeValue(null, "t") == "s" }
                    "v" -> inValue = true
                }
                XMLStreamConstants.CHARACTERS -> if (inValue && column >= 0) {
                    val text = reader.text
                    row[column] = if (isShared) strings.getOrNull(text.trim().toIntOrNull() ?: -1).orEmpty() else text
                }
                XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
                    "v" -> inValue = false
                    "c" -> column = -1
                    "row" -> onRow(row)
                }
            }
        }
    }

    /** `B12` is column 1: the letters are a base-26 number, A = 0. */
    internal fun columnOf(reference: String?): Int {
        val letters = reference?.takeWhile { it.isLetter() }.orEmpty()
        if (letters.isEmpty()) return -1
        return letters.uppercase().fold(0) { total, letter -> total * LETTERS + (letter - 'A' + 1) } - 1
    }
}
