package au.homefinder.app.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException

/** Where the API is and the key it needs. The key is never printed. */
data class ApiConfig(val baseUrl: String, val apiKey: String) {
    override fun toString(): String = "ApiConfig(baseUrl=$baseUrl, apiKey=***)"

    fun url(path: String): String = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
}

sealed interface HttpResult {
    data class Response(val status: Int, val body: String) : HttpResult
    data class Failure(val message: String) : HttpResult
}

/** One GET request; separate from Ktor so the status logic can be tested without a network. */
fun interface HttpGet {
    suspend fun get(url: String, headers: Map<String, String>): HttpResult
}

class KtorHttpGet(private val client: HttpClient = defaultClient()) : HttpGet {
    override suspend fun get(url: String, headers: Map<String, String>): HttpResult = try {
        val response = client.get(url) { headers.forEach { (name, value) -> header(name, value) } }
        HttpResult.Response(response.status.value, response.bodyAsText())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        HttpResult.Failure(failure.message ?: failure::class.simpleName.orEmpty())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L

        fun defaultClient() = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MILLIS
                connectTimeoutMillis = TIMEOUT_MILLIS
            }
        }
    }
}
