package au.homefinder.server

import au.homefinder.shared.api.ApiContract

data class ServerConfig(val host: String, val port: Int, val apiKey: String) {
    /** Never print the key. */
    override fun toString(): String = "ServerConfig(host=$host, port=$port, apiKey=***)"
}

sealed interface ConfigResult {
    data class Loaded(val config: ServerConfig) : ConfigResult
    data class Invalid(val problems: List<String>) : ConfigResult
}

object ServerConfigLoader {
    const val HOST_VARIABLE: String = "API_HOST"
    const val PORT_VARIABLE: String = "API_PORT"
    const val API_KEY_VARIABLE: String = "API_KEY"
    const val DEFAULT_HOST: String = "0.0.0.0"
    const val MIN_API_KEY_LENGTH: Int = 16
    private const val MIN_PORT = 1
    private const val MAX_PORT = 65_535

    /** Reads the configuration from environment variables; every problem is reported at once. */
    fun fromEnv(env: Map<String, String>): ConfigResult {
        val problems = mutableListOf<String>()

        val host = env[HOST_VARIABLE]?.trim().orEmpty().ifEmpty { DEFAULT_HOST }

        val portText = env[PORT_VARIABLE]?.trim().orEmpty()
        val port = if (portText.isEmpty()) {
            ApiContract.DEFAULT_PORT
        } else {
            portText.toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT }
                ?: run { problems += "$PORT_VARIABLE must be a number from $MIN_PORT to $MAX_PORT, but was '$portText'"; 0 }
        }

        val apiKey = env[API_KEY_VARIABLE]?.trim().orEmpty()
        if (apiKey.isEmpty()) {
            problems += "$API_KEY_VARIABLE must be set"
        } else if (apiKey.length < MIN_API_KEY_LENGTH) {
            problems += "$API_KEY_VARIABLE must be at least $MIN_API_KEY_LENGTH characters"
        }

        return if (problems.isEmpty()) ConfigResult.Loaded(ServerConfig(host, port, apiKey)) else ConfigResult.Invalid(problems)
    }
}
