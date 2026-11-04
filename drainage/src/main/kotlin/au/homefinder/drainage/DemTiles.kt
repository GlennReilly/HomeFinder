package au.homefinder.drainage

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What [DemTiles.open] found. */
sealed interface DemOpenResult {
    data class Opened(val tiles: DemTiles) : DemOpenResult
    data class Failed(val reason: String) : DemOpenResult
}

/** A window of ground heights, and the tiles that overlap it but could not be read (their pixels are no data). */
data class DemWindow(val grid: ElevationGrid?, val unreadable: List<Path>)

/**
 * The 5 m DEM as downloaded by pipeline/scripts/download-dem.sh: float GeoTIFFs in degrees on one shared pixel grid, each carrying the
 * standard ModelPixelScale and ModelTiepoint tags. The JDK's own TIFF reader reads them, so no GDAL is needed (ADR 0040).
 */
class DemTiles private constructor(
    private val tiles: List<TileHeader>,
    private val pixelDegX: Double,
    private val pixelDegY: Double,
    private val anchorWestDeg: Double,
    private val anchorNorthDeg: Double,
) {
    private data class TileHeader(val file: Path, val columns: Int, val rows: Int, val westDeg: Double, val northDeg: Double)

    val tileCount: Int get() = tiles.size

    /** The heights inside the box (widened to whole pixels), or a null grid when no tile reaches it. */
    fun window(westDeg: Double, southDeg: Double, eastDeg: Double, northDeg: Double): DemWindow {
        val firstColumn = floor((westDeg - anchorWestDeg) / pixelDegX).toInt()
        val lastColumn = ceil((eastDeg - anchorWestDeg) / pixelDegX).toInt()
        val firstRow = floor((anchorNorthDeg - northDeg) / pixelDegY).toInt()
        val lastRow = ceil((anchorNorthDeg - southDeg) / pixelDegY).toInt()
        val columns = lastColumn - firstColumn
        val rows = lastRow - firstRow
        if (columns <= 0 || rows <= 0) return DemWindow(null, emptyList())

        val heights = FloatArray(columns * rows) { Float.NaN }
        val unreadable = ArrayList<Path>()
        var touched = false
        for (tile in tiles) {
            val tileFirstColumn = ((tile.westDeg - anchorWestDeg) / pixelDegX).roundToInt()
            val tileFirstRow = ((anchorNorthDeg - tile.northDeg) / pixelDegY).roundToInt()
            val fromColumn = max(firstColumn, tileFirstColumn)
            val toColumn = min(lastColumn, tileFirstColumn + tile.columns)
            val fromRow = max(firstRow, tileFirstRow)
            val toRow = min(lastRow, tileFirstRow + tile.rows)
            if (fromColumn >= toColumn || fromRow >= toRow) continue
            touched = true
            val pixels = readPixels(tile)
            if (pixels == null) {
                unreadable.add(tile.file)
                continue
            }
            for (row in fromRow until toRow) {
                val source = (row - tileFirstRow) * tile.columns + (fromColumn - tileFirstColumn)
                val target = (row - firstRow) * columns + (fromColumn - firstColumn)
                System.arraycopy(pixels, source, heights, target, toColumn - fromColumn)
            }
        }
        if (!touched) return DemWindow(null, unreadable)
        val grid = ElevationGrid(
            columns, rows, anchorWestDeg + firstColumn * pixelDegX, anchorNorthDeg - firstRow * pixelDegY, pixelDegX, pixelDegY, heights,
        )
        return DemWindow(grid, unreadable)
    }

    private fun readPixels(tile: TileHeader): FloatArray? = try {
        withReader(tile.file) { reader ->
            val raster = reader.read(0).raster
            if (raster.numBands != 1 || raster.width != tile.columns || raster.height != tile.rows) null
            else raster.getSamples(0, 0, raster.width, raster.height, 0, null as FloatArray?)
        }
    } catch (failure: IOException) {
        null // A truncated download: the whole tile counts as no data.
    }

    companion object {
        private const val PIXEL_SIZE_TOLERANCE_DEG = 1e-9
        private val TIFF_EXTENSIONS = setOf("tif", "tiff")

        /** Reads the headers of every `.tif` in [directory]; pixels are read later, only for the windows asked for. */
        fun open(directory: Path): DemOpenResult {
            if (!Files.isDirectory(directory)) return DemOpenResult.Failed("Not a directory: $directory")
            val files = Files.list(directory).use { stream -> stream.filter { it.extension.lowercase() in TIFF_EXTENSIONS }.sorted().toList() }
            if (files.isEmpty()) return DemOpenResult.Failed("Missing GeoTIFF files in $directory")
            val headers = mutableListOf<TileHeader>()
            var pixelX = Double.NaN
            var pixelY = Double.NaN
            for (file in files) {
                val parsed = try {
                    TiffHeaderReader.read(file)
                } catch (failure: IOException) {
                    return DemOpenResult.Failed("Could not read ${file.name} as a GeoTIFF: ${failure.message}")
                } ?: return DemOpenResult.Failed("Could not read ${file.name} as a GeoTIFF: it is not a TIFF with the size and georeferencing tags")
                if (pixelX.isNaN()) {
                    pixelX = parsed.pixelDegX
                    pixelY = parsed.pixelDegY
                } else if (abs(parsed.pixelDegX - pixelX) > PIXEL_SIZE_TOLERANCE_DEG || abs(parsed.pixelDegY - pixelY) > PIXEL_SIZE_TOLERANCE_DEG) {
                    return DemOpenResult.Failed(
                        "${file.name} has a different pixel size from the other tiles (${parsed.pixelDegX} x ${parsed.pixelDegY}, not $pixelX x $pixelY)",
                    )
                }
                headers += TileHeader(file, parsed.columns, parsed.rows, parsed.westDeg, parsed.northDeg)
            }
            val first = headers.first()
            return DemOpenResult.Opened(DemTiles(headers, pixelX, pixelY, first.westDeg, first.northDeg))
        }

        private fun <T> withReader(file: Path, block: (ImageReader) -> T): T {
            val stream = ImageIO.createImageInputStream(file.toFile()) ?: throw IOException("cannot open $file")
            stream.use {
                val reader = ImageIO.getImageReadersByFormatName("tiff").asSequence().firstOrNull() ?: throw IOException("this JDK has no TIFF reader")
                try {
                    reader.setInput(stream)
                    return block(reader)
                } catch (failure: IllegalArgumentException) {
                    throw IOException("not a readable TIFF: ${failure.message}", failure)
                } finally {
                    reader.dispose()
                }
            }
        }
    }
}
