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
            println("${ServerInfo.name} listening on http://${config.host}:${config.port}")
            embeddedServer(Netty, host = config.host, port = config.port) { module(config) }.start(wait = true)
        }
    }
}
