package au.homefinder.census

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.system.exitProcess

private const val USAGE = "Usage: census <unpacked DataPack directory> <output csv>"
private const val EXIT_BAD_INPUT = 2
private const val ARGUMENTS = 2
private const val TABLE_FILE = "2021Census_G37_NSW_SAL.csv"
private const val WORKBOOK_PREFIX = "2021Census_geog_desc"

/** Turns the ABS 2021 DataPack (table G37 and the geography workbook) into one CSV of counts per named suburb (ADR 0044). */
fun main(args: Array<String>) {
    if (args.size != ARGUMENTS) fail(USAGE)
    val root = Path.of(args[0])
    if (!Files.isDirectory(root)) fail("Not a directory: $root")
    val files = Files.walk(root).use { stream -> stream.filter { Files.isRegularFile(it) }.toList() }
    val table = files.firstOrNull { it.name == TABLE_FILE } ?: fail("No $TABLE_FILE under $root; run download-census.sh")
    val workbook = files.firstOrNull { it.name.startsWith(WORKBOOK_PREFIX) && it.extension == "xlsx" }
        ?: fail("No $WORKBOOK_PREFIX*.xlsx workbook under $root; it holds the suburb names")
    val names = when (val read = SalNames.read(workbook)) {
        is SalNamesResult.Read -> read.namesByCode
        is SalNamesResult.Failed -> fail(read.reason)
    }
    val suburbs = when (val parsed = G37.parse(Files.readAllLines(table))) {
        is G37Result.Parsed -> parsed.suburbs
        is G37Result.Malformed -> fail("$table line ${parsed.lineNumber}: ${parsed.reason}")
    }
    val (lines, unnamed) = CensusCsv.format(suburbs, names)
    Files.write(Path.of(args[1]), lines)
    println("${names.size} suburb names, ${suburbs.size} suburbs in G37, ${lines.size - 1} written, $unnamed without a name")
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(EXIT_BAD_INPUT)
}
