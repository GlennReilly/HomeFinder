package au.homefinder.server

import au.homefinder.shared.api.ApiContract

/** How to reach PostGIS. The password is never printed. */
data class DatabaseConfig(val url: String, val user: String, val password: String) {
    override fun toString(): String = "DatabaseConfig(url=$url, user=$user, password=***)"
}

data class ServerConfig(
    val host: String,
    val port: Int,
    val apiKey: String,
    val database: DatabaseConfig? = null,
) {
    /** Never print the key or the database password. */
    override fun toString(): String = "ServerConfig(host=$host, port=$port, apiKey=***, database=$database)"
}

sealed interface ConfigResult {
    data class Loaded(val config: ServerConfig) : ConfigResult
    data class Invalid(val problems: List<String>) : ConfigResult
}

object ServerConfigLoader {
    const val HOST_VARIABLE: String = "API_HOST"
    const val PORT_VARIABLE: String = "API_PORT"
    const val API_KEY_VARIABLE: String = "API_KEY"
    const val DB_URL_VARIABLE: String = "DB_URL"
    const val DB_USER_VARIABLE: String = "DB_USER"
    const val DB_PASSWORD_VARIABLE: String = "DB_PASSWORD"
    const val DEFAULT_HOST: String = "0.0.0.0"
    const val MIN_API_KEY_LENGTH: Int = 16
    const val JDBC_POSTGRES_PREFIX: String = "jdbc:postgresql:"
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

        val database = loadDatabase(env, problems)

        return if (problems.isEmpty()) ConfigResult.Loaded(ServerConfig(host, port, apiKey, database)) else ConfigResult.Invalid(problems)
    }

    /** The database is optional, but if any of its variables is set all of them must be, and the URL must be PostgreSQL. */
    private fun loadDatabase(env: Map<String, String>, problems: MutableList<String>): DatabaseConfig? {
        val url = env[DB_URL_VARIABLE]?.trim().orEmpty()
        val user = env[DB_USER_VARIABLE]?.trim().orEmpty()
        val password = env[DB_PASSWORD_VARIABLE].orEmpty()
        if (url.isEmpty() && user.isEmpty() && password.isEmpty()) return null
        if (url.isEmpty()) problems += "$DB_URL_VARIABLE must be set when the database is configured"
        else if (!url.startsWith(JDBC_POSTGRES_PREFIX)) problems += "$DB_URL_VARIABLE must start with $JDBC_POSTGRES_PREFIX"
        if (user.isEmpty()) problems += "$DB_USER_VARIABLE must be set when the database is configured"
        if (password.isEmpty()) problems += "$DB_PASSWORD_VARIABLE must be set when the database is configured"
        return if (url.startsWith(JDBC_POSTGRES_PREFIX) && user.isNotEmpty() && password.isNotEmpty()) DatabaseConfig(url, user, password) else null
    }
}
