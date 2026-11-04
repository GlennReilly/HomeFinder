package au.homefinder.drainage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private const val USAGE = "Usage: drainage <dem directory> <lots csv> <output csv> [stream catchment in hectares]"
private const val EXIT_BAD_INPUT = 2
private const val MINIMUM_ARGUMENTS = 3
private const val MAXIMUM_ARGUMENTS = 4

/** Height above nearest drainage for every lot in a CSV of representative points (ADR 0040). */
fun main(args: Array<String>) {
    if (args.size !in MINIMUM_ARGUMENTS..MAXIMUM_ARGUMENTS) fail(USAGE)
    val streamHa = args.getOrNull(MAXIMUM_ARGUMENTS - 1)?.toDoubleOrNull() ?: DrainageAnalysis.DEFAULT_STREAM_CATCHMENT_HA
    if (args.size == MAXIMUM_ARGUMENTS && args[MAXIMUM_ARGUMENTS - 1].toDoubleOrNull() == null) fail("The stream catchment must be a number of hectares. $USAGE")
    val tiles = when (val opened = DemTiles.open(Path.of(args[0]))) {
        is DemOpenResult.Opened -> opened.tiles
        is DemOpenResult.Failed -> fail(opened.reason)
    }
    val points = when (val parsed = LotCsv.parsePoints(Files.readAllLines(Path.of(args[1])))) {
        is PointsResult.Parsed -> parsed.points
        is PointsResult.Malformed -> fail("${args[1]} line ${parsed.lineNumber}: ${parsed.reason}")
    }
    println("${points.size} lots, ${tiles.tileCount} DEM tiles, streams from $streamHa ha")
    val results = DrainageRunner.run(tiles, points, streamHa) { println(it) }
    Files.write(Path.of(args[2]), LotCsv.formatResults(results))
    println("Wrote ${results.size} lots to ${args[2]}")
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(EXIT_BAD_INPUT)
}
