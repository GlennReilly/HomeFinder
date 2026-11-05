package au.homefinder.census

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private const val USAGE = "Usage: crime <BOCSAR SuburbData.csv> <output csv>"
private const val EXIT_BAD_INPUT = 2
private const val ARGUMENTS = 2

/** Turns the BOCSAR suburb crime file into one CSV of violent and property incidents per suburb over the latest 24 months (ADR 0051). */
fun main(args: Array<String>) {
    if (args.size != ARGUMENTS) fail(USAGE)
    val file = Path.of(args[0])
    if (!Files.isRegularFile(file)) fail("Not a file: $file")
    when (val result = Crime.read(file)) {
        is CrimeResult.Failed -> fail(result.reason)
        is CrimeResult.Read -> {
            Files.write(Path.of(args[1]), CrimeCsv.format(result))
            println("${result.suburbs.size} suburbs, ${result.windowText} (data to ${result.asOf})")
        }
    }
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(EXIT_BAD_INPUT)
}
