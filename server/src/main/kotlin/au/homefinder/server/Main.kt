package au.homefinder.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File
import kotlin.system.exitProcess

private const val EXIT_INVALID_CONFIG = 2

/** The page the server hands out at `/`; without it a configured web root is a mistake worth stopping for. */
private const val WEB_INDEX = "index.html"

fun main() {
    when (val result = ServerConfigLoader.fromEnv(System.getenv())) {
        is ConfigResult.Invalid -> {
            System.err.println("Cannot start ${ServerInfo.name}:")
            result.problems.forEach { System.err.println("  - $it") }
            exitProcess(EXIT_INVALID_CONFIG)
        }
        is ConfigResult.Loaded -> {
            val config = result.config
            config.webRoot?.let { root ->
                if (!File(root, WEB_INDEX).isFile) {
                    System.err.println("Cannot start ${ServerInfo.name}: WEB_ROOT '$root' has no $WEB_INDEX (build it with ./gradlew :webApp:jsBrowserDistribution)")
                    exitProcess(EXIT_INVALID_CONFIG)
                }
            }
            val dataSource = config.database?.let { PostgisSuburbTiles.connect(it) }
            Runtime.getRuntime().addShutdownHook(Thread { dataSource?.close() })
            println("${ServerInfo.name} listening on http://${config.host}:${config.port}")
            println(if (config.webRoot == null) "Web app: not served (API only)" else "Web app: served from ${config.webRoot}")
            println(if (dataSource == null) "Database: not configured (tiles unavailable)" else "Database: ${config.database}")
            val tiles = dataSource?.let { PostgisSuburbTiles(it) }
            val suburbs = dataSource?.let { PostgisSuburbDirectory(it) }
            val lotTiles = dataSource?.let { PostgisLotTiles(it) }
            val lots = dataSource?.let { PostgisLotDirectory(it) }
            embeddedServer(Netty, host = config.host, port = config.port) { module(config, tiles, suburbs, lotTiles, lots) }.start(wait = true)
        }
    }
}
