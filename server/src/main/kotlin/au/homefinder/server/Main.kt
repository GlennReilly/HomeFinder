package au.homefinder.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlin.system.exitProcess

private const val EXIT_INVALID_CONFIG = 2

fun main() {
    when (val result = ServerConfigLoader.fromEnv(System.getenv())) {
        is ConfigResult.Invalid -> {
            System.err.println("Cannot start ${ServerInfo.name}:")
            result.problems.forEach { System.err.println("  - $it") }
            exitProcess(EXIT_INVALID_CONFIG)
        }
        is ConfigResult.Loaded -> {
            val config = result.config
            val dataSource = config.database?.let { PostgisSuburbTiles.connect(it) }
            Runtime.getRuntime().addShutdownHook(Thread { dataSource?.close() })
            println("${ServerInfo.name} listening on http://${config.host}:${config.port}")
            println(if (dataSource == null) "Database: not configured (tiles unavailable)" else "Database: ${config.database}")
            val tiles = dataSource?.let { PostgisSuburbTiles(it) }
            embeddedServer(Netty, host = config.host, port = config.port) { module(config, tiles) }.start(wait = true)
        }
    }
}
