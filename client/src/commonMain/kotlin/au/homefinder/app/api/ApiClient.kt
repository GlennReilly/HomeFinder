package au.homefinder.app.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.readRawBytes
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
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

/** The HTTP methods of the requests that change something; [wire] is the method's name on the wire. */
enum class HttpVerb(val wire: String) {
    PUT("PUT"),
    DELETE("DELETE"),
    POST("POST"),
}

/** One request that changes something ([HttpVerb]); the body, if any, is JSON. Separate from Ktor so it can be tested without a network. */
fun interface HttpWrite {
    suspend fun send(method: HttpVerb, url: String, headers: Map<String, String>, body: String?): HttpResult
}

/** The answer to a request whose body is a file (the PDF export, ADR 0080), not text. */
sealed interface BytesResult {
    /** [bytes] is the body as sent: the file on success, or the JSON error otherwise. */
    class Response(val status: Int, val bytes: ByteArray) : BytesResult

    data class Failure(val message: String) : BytesResult
}

/** One request whose answer is a file; the body, if any, is JSON. [timeoutMillis] replaces the usual timeout, for answers that take a while to make. */
fun interface HttpBytes {
    suspend fun fetch(method: HttpVerb, url: String, headers: Map<String, String>, body: String?, timeoutMillis: Long): BytesResult
}

/** The Ktor client behind every kind of request. */
class KtorHttp(private val client: HttpClient = defaultClient()) : HttpGet, HttpWrite, HttpBytes {
    override suspend fun fetch(method: HttpVerb, url: String, headers: Map<String, String>, body: String?, timeoutMillis: Long): BytesResult = try {
        val response = client.request(url) {
            this.method = HttpMethod.parse(method.wire)
            timeout { requestTimeoutMillis = timeoutMillis }
            headers.forEach { (name, value) -> header(name, value) }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        BytesResult.Response(response.status.value, response.readRawBytes())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        BytesResult.Failure(failure.message ?: failure::class.simpleName.orEmpty())
    }

    override suspend fun send(method: HttpVerb, url: String, headers: Map<String, String>, body: String?): HttpResult = try {
        val response = client.request(url) {
            this.method = HttpMethod.parse(method.wire)
            headers.forEach { (name, value) -> header(name, value) }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        HttpResult.Response(response.status.value, response.bodyAsText())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        HttpResult.Failure(failure.message ?: failure::class.simpleName.orEmpty())
    }

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
