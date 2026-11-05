package au.homefinder.census

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private const val USAGE = "Usage: catchments <unpacked catchments directory> <output csv>"
private const val EXIT_BAD_INPUT = 2
private const val ARGUMENTS = 2

/** Turns the Department of Education's catchment shapefiles (current primary and secondary) into one CSV of zones with WKT shapes (ADR 0056). */
fun main(args: Array<String>) {
    if (args.size != ARGUMENTS) fail(USAGE)
    val directory = Path.of(args[0])
    if (!Files.isDirectory(directory)) fail("Not a directory: $directory")
    when (val result = Catchments.read(directory)) {
        is CatchmentsResult.Failed -> fail(result.reason)
        is CatchmentsResult.Read -> {
            Files.write(Path.of(args[1]), CatchmentsCsv.format(result))
            println("${result.catchments.size} catchments written (${result.skipped} skipped), enrolment year ${result.enrolmentYear ?: "unknown"}")
        }
    }
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(EXIT_BAD_INPUT)
}
