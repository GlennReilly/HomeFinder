package au.homefinder.census

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private const val USAGE = "Usage: seifa <SEIFA 2021 SAL workbook .xlsx> <output csv>"
private const val EXIT_BAD_INPUT = 2
private const val ARGUMENTS = 2

/** Turns the ABS SEIFA 2021 suburb workbook into one CSV of IRSAD score, decile and population per named suburb (ADR 0049). */
fun main(args: Array<String>) {
    if (args.size != ARGUMENTS) fail(USAGE)
    val workbook = Path.of(args[0])
    if (!Files.isRegularFile(workbook)) fail("Not a file: $workbook")
    when (val result = Seifa.read(workbook)) {
        is SeifaResult.Failed -> fail(result.reason)
        is SeifaResult.Read -> {
            Files.write(Path.of(args[1]), SeifaCsv.format(result.suburbs))
            println("${result.suburbs.size} suburbs with an IRSAD score written, ${result.withoutScore} without one")
        }
    }
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(EXIT_BAD_INPUT)
}
