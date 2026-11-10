package au.homefinder.server

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import java.util.Calendar

/**
 * Text made safe for the PDF's standard Helvetica, which only has the WinAnsi characters: a few common ones outside it (stars, diamonds, maths signs)
 * get a plain stand-in, control characters are dropped, and anything else the font cannot show becomes "?", so drawing never throws (ADR 0080).
 */
class PdfText(private val font: PDType1Font) {
    private val known = HashMap<Int, String>()

    fun safe(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            out.append(known.getOrPut(codePoint) { replacement(codePoint) })
            i += Character.charCount(codePoint)
        }
        return out.toString()
    }

    private fun replacement(codePoint: Int): String {
        STAND_INS[codePoint]?.let { return it }
        if (codePoint == TAB) return " "
        if (Character.isISOControl(codePoint)) return ""
        val text = String(Character.toChars(codePoint))
        return try {
            font.getStringWidth(text)
            text
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") unsupported: Exception) {
            UNSUPPORTED
        }
    }

    private companion object {
        const val TAB = '\t'.code
        const val UNSUPPORTED = "?"
        val STAND_INS: Map<Int, String> = mapOf(
            0x2605 to "*", // black star, as in the Homely score
            0x2606 to "*",
            0x25C6 to "*", // black diamond, as on the NAPLAN map label
            0x25C7 to "*",
            0x2265 to ">=",
            0x2264 to "<=",
            0x2192 to "->",
            0x2212 to "-", // minus sign
            0x2009 to " ", // thin space
            0x202F to " ", // narrow no-break space
            0x2011 to "-", // non-breaking hyphen
        )
    }
}

/**
 * Draws the export (ADR 0080) with Apache PDFBox and its standard Helvetica (no font files are embedded): a cover with the date, the filters used and
 * the disclaimer, then each suburb from a new page, its rows in a label column and a value column under the card group titles. Long text wraps; a row is
 * kept on one page where it fits.
 */
object SuburbPdf {
    const val TITLE: String = "HomeFinder: suburbs matching your filters"
    const val NO_FILTERS_TEXT: String = "No filter lines were sent."
    const val CREATOR: String = "HomeFinder"

    private const val MARGIN = 50f
    private const val LABEL_WIDTH = 150f
    private const val COLUMN_GAP = 12f
    private const val SUB_INDENT = 8f
    private const val LEADING = 1.3f
    private const val TITLE_SIZE = 18f
    private const val SUBURB_SIZE = 15f
    private const val GROUP_SIZE = 11.5f
    private const val BODY_SIZE = 10f
    private const val SUB_SIZE = 9.5f
    private const val SOURCE_SIZE = 8f
    private const val FOOTER_SIZE = 8f
    private const val FOOTER_Y = 28f
    private const val GROUP_GAP = 8f
    private const val ROW_GAP = 4f
    private const val SECTION_GAP = 10f
    private const val UNITS_PER_EM = 1000f
    private const val GREY = 0.35f
    private const val BLACK = 0f

    /** The PDF's bytes for [document]. */
    fun render(document: ExportDocument): ByteArray = PDDocument().use { pdf ->
        val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val writer = Writer(pdf, regular, bold, document.date)
        cover(writer, document)
        // The first suburb follows the cover on the same page, so the cover is not left mostly blank; the others start pages of their own.
        document.suburbs.forEachIndexed { index, suburb -> suburb(writer, suburb, newPage = index > 0) }
        writer.finish()
        pdf.documentInformation.apply {
            title = TITLE
            creator = CREATOR
            creationDate = Calendar.getInstance()
        }
        ByteArrayOutputStream().also { pdf.save(it) }.toByteArray()
    }

    private fun cover(writer: Writer, document: ExportDocument) {
        writer.newPage()
        writer.paragraph(TITLE, writer.bold, TITLE_SIZE)
        writer.gap(ROW_GAP)
        val count = document.suburbs.size
        writer.paragraph("Made on ${document.date} · $count ${if (count == 1) "suburb" else "suburbs"}", writer.regular, BODY_SIZE)
        if (document.notFound > 0) writer.paragraph("${document.notFound} of the suburbs asked for could not be found and are left out.", writer.regular, BODY_SIZE)
        writer.gap(SECTION_GAP)
        writer.paragraph("Filters used", writer.bold, GROUP_SIZE)
        if (document.filters.isEmpty()) writer.paragraph(NO_FILTERS_TEXT, writer.regular, BODY_SIZE)
        document.filters.forEach { writer.paragraph("- $it", writer.regular, BODY_SIZE, indent = SUB_INDENT) }
        writer.gap(SECTION_GAP)
        writer.paragraph("Suburbs", writer.bold, GROUP_SIZE)
        writer.paragraph(document.suburbs.joinToString(", ") { it.name }, writer.regular, BODY_SIZE)
        writer.gap(SECTION_GAP)
        writer.paragraph(
            "Every row of the card is listed with its note and source; a row with no data says so. ${document.disclaimer}",
            writer.regular, SOURCE_SIZE, grey = true,
        )
    }

    private fun suburb(writer: Writer, suburb: ExportSuburb, newPage: Boolean) {
        if (newPage) writer.newPage() else writer.gap(SECTION_GAP * 2)
        writer.paragraph(suburb.name, writer.bold, SUBURB_SIZE)
        suburb.postcode?.let { writer.paragraph("Postcode $it", writer.regular, BODY_SIZE, grey = true) }
        if (suburb.groups.isEmpty()) writer.paragraph("Nothing to show for this suburb yet.", writer.regular, BODY_SIZE)
        suburb.groups.forEach { group ->
            writer.gap(GROUP_GAP)
            // A group title is never left alone at the foot of a page: it needs room for itself and its first row.
            writer.paragraph(group.title, writer.bold, GROUP_SIZE, keepWith = writer.rowHeight(group.lines.first()))
            group.lines.forEach { writer.row(it) }
        }
    }

    /** One line of text at a size, with its colour, in the label or the value column. */
    private class Piece(val text: String, val font: PDType1Font, val size: Float, val grey: Boolean, val indent: Float = 0f)

    /** Where the text goes next, on which page; it starts a new page when the next piece would run into the bottom margin. */
    private class Writer(private val pdf: PDDocument, val regular: PDType1Font, val bold: PDType1Font, private val date: String) {
        private val safeRegular = PdfText(regular)
        private val safeBold = PdfText(bold)
        private var stream: PDPageContentStream? = null
        private var y = 0f
        private var pages = 0
        private val width = PDRectangle.A4.width - 2 * MARGIN
        private val valueX = MARGIN + LABEL_WIDTH + COLUMN_GAP
        private val valueWidth = width - LABEL_WIDTH - COLUMN_GAP

        fun newPage() {
            stream?.close()
            val page = PDPage(PDRectangle.A4)
            pdf.addPage(page)
            pages++
            stream = PDPageContentStream(pdf, page).also { footer(it) }
            y = PDRectangle.A4.height - MARGIN
        }

        fun finish() {
            stream?.close()
            stream = null
        }

        fun gap(points: Float) {
            y -= points
        }

        /** Wrapped text across the full width; [keepWith] is the height that must fit below it on the same page (a group's first row). */
        fun paragraph(text: String, font: PDType1Font, size: Float, indent: Float = 0f, grey: Boolean = false, keepWith: Float = 0f) {
            val lines = wrap(text, font, size, width - indent)
            ensure(lines.size * size * LEADING + keepWith)
            lines.forEach { draw(Piece(it, font, size, grey), MARGIN + indent) }
        }

        /** The height a row takes: the taller of its label column and its value column. */
        fun rowHeight(line: ExportLine): Float {
            val (labels, values) = columns(line)
            return maxOf(labels.sumOf { (it.size * LEADING).toDouble() }, values.sumOf { (it.size * LEADING).toDouble() }).toFloat() + ROW_GAP
        }

        /** A row: its label (bold) on the left, its value, school lines and source on the right; kept on one page when it fits on one. */
        fun row(line: ExportLine) {
            val (labels, values) = columns(line)
            ensure(rowHeight(line))
            // Drawn line by line, label and value side by side; a row taller than a page simply carries on onto the next one.
            for (i in 0 until maxOf(labels.size, values.size)) {
                val label = labels.getOrNull(i)
                val value = values.getOrNull(i)
                val height = maxOf(label?.size ?: 0f, value?.size ?: 0f) * LEADING
                ensure(height)
                label?.let { text(it, MARGIN) }
                value?.let { text(it, valueX + it.indent) }
                y -= height
            }
            y -= ROW_GAP
        }

        private fun columns(line: ExportLine): Pair<List<Piece>, List<Piece>> {
            val labels = wrap(line.label, bold, BODY_SIZE, LABEL_WIDTH).map { Piece(it, bold, BODY_SIZE, grey = false) }
            val values = buildList {
                line.value?.let { value -> wrap(value, regular, BODY_SIZE, valueWidth).forEach { add(Piece(it, regular, BODY_SIZE, grey = false)) } }
                line.subLines.forEach { sub ->
                    val indent = if (sub.startsWith(" ")) SUB_INDENT else 0f
                    wrap(sub.trim(), regular, SUB_SIZE, valueWidth - indent).forEach { add(Piece(it, regular, SUB_SIZE, grey = false, indent = indent)) }
                }
                line.note?.let { note -> wrap(note, regular, SUB_SIZE, valueWidth).forEach { add(Piece(it, regular, SUB_SIZE, grey = true)) } }
                line.source?.let { source -> wrap("Source: $source", regular, SOURCE_SIZE, valueWidth).forEach { add(Piece(it, regular, SOURCE_SIZE, grey = true)) } }
            }
            return labels to values
        }

        private fun ensure(height: Float) {
            if (stream == null || y - height < MARGIN) newPage()
        }

        private fun draw(piece: Piece, x: Float) {
            val height = piece.size * LEADING
            ensure(height)
            text(piece, x)
            y -= height
        }

        private fun text(piece: Piece, x: Float) {
            val out = stream ?: return
            out.beginText()
            out.setFont(piece.font, piece.size)
            val shade = if (piece.grey) GREY else BLACK
            out.setNonStrokingColor(shade, shade, shade)
            out.newLineAtOffset(x, y - piece.size)
            out.showText(piece.text)
            out.endText()
        }

        private fun footer(out: PDPageContentStream) {
            out.beginText()
            out.setFont(regular, FOOTER_SIZE)
            out.setNonStrokingColor(GREY, GREY, GREY)
            out.newLineAtOffset(MARGIN, FOOTER_Y)
            out.showText(safeRegular.safe("HomeFinder export · $date · page $pages"))
            out.endText()
        }

        private fun safe(text: String, font: PDType1Font) = (if (font === bold) safeBold else safeRegular).safe(text)

        private fun widthOf(text: String, font: PDType1Font, size: Float) = font.getStringWidth(text) / UNITS_PER_EM * size

        /** Breaks safe text into lines no wider than [maxWidth], at spaces where it can and inside a word only when a word is wider than a line. */
        fun wrap(text: String, font: PDType1Font, size: Float, maxWidth: Float): List<String> {
            val lines = mutableListOf<String>()
            text.split('\n').forEach { paragraph ->
                var current = ""
                safe(paragraph, font).split(' ').filter { it.isNotEmpty() }.forEach { word ->
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (widthOf(candidate, font, size) <= maxWidth) {
                        current = candidate
                    } else {
                        if (current.isNotEmpty()) lines += current
                        current = ""
                        var rest = word
                        while (widthOf(rest, font, size) > maxWidth) {
                            val fits = (1..rest.length).last { widthOf(rest.take(it), font, size) <= maxWidth || it == 1 }
                            lines += rest.take(fits)
                            rest = rest.drop(fits)
                        }
                        current = rest
                    }
                }
                lines += current
            }
            return lines.ifEmpty { listOf("") }
        }
    }
}
