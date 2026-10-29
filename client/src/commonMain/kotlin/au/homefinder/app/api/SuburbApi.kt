package au.homefinder.app.api

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.api.SuburbRef
import au.homefinder.shared.model.ApiJson
import au.homefinder.shared.model.SuburbReport
import kotlin.math.abs
import kotlin.math.roundToLong

sealed interface LookupResult<out T> {
    data class Found<T>(val value: T) : LookupResult<T>
    data object NotFound : LookupResult<Nothing>
    data object Unauthorized : LookupResult<Nothing>
    data class Failed(val reason: String) : LookupResult<Nothing>
}

/** The suburb endpoints of the API (ADR 0016). The key goes only in the request header. */
class SuburbApi(private val config: ApiConfig, private val http: HttpGet) {
    suspend fun suburbAt(latitude: Double, longitude: Double): LookupResult<SuburbRef> {
        val query = "${ApiContract.LATITUDE_PARAMETER}=${formatCoordinate(latitude)}&${ApiContract.LONGITUDE_PARAMETER}=${formatCoordinate(longitude)}"
        return get(config.url(ApiContract.SUBURBS_AT_PATH) + "?" + query)
    }

    suspend fun report(suburbId: String): LookupResult<SuburbReport> =
        get(config.url(ApiContract.SUBURBS_PATH + "/" + encodePathSegment(suburbId)))

    private suspend inline fun <reified T> get(url: String): LookupResult<T> =
        when (val result = http.get(url, mapOf(ApiContract.API_KEY_HEADER to config.apiKey))) {
            is HttpResult.Failure -> LookupResult.Failed(result.message)
            is HttpResult.Response -> when (result.status) {
                HTTP_OK -> runCatching { ApiJson.decodeFromString<T>(result.body) }
                    .fold({ LookupResult.Found(it) }, { LookupResult.Failed("the response was not understood") })
                HTTP_NOT_FOUND -> LookupResult.NotFound
                HTTP_UNAUTHORIZED -> LookupResult.Unauthorized
                else -> LookupResult.Failed("the server answered ${result.status}")
            }
        }

    companion object {
        private const val HTTP_OK = 200
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_UNAUTHORIZED = 401
        private const val COORDINATE_DECIMALS = 7
        private const val DECIMAL_SCALE = 10_000_000L
        private const val UNRESERVED = "-._~"
        private const val HEX = "0123456789ABCDEF"
        private const val BYTE_MASK = 0xFF
        private const val NIBBLE_SHIFT = 4
        private const val NIBBLE_MASK = 0xF

        /** A plain decimal with seven places (about a centimetre); never scientific notation, which the API would reject. */
        fun formatCoordinate(value: Double): String {
            val scaled = (abs(value) * DECIMAL_SCALE).roundToLong()
            val whole = scaled / DECIMAL_SCALE
            val fraction = (scaled % DECIMAL_SCALE).toString().padStart(COORDINATE_DECIMALS, '0')
            val sign = if (value < 0 && scaled != 0L) "-" else ""
            return "$sign$whole.$fraction"
        }

        /** Percent-encodes everything but unreserved characters, so an id can never alter the path. */
        fun encodePathSegment(segment: String): String = buildString {
            for (byte in segment.encodeToByteArray()) {
                val value = byte.toInt() and BYTE_MASK
                val char = value.toChar()
                if (char.isLetterOrDigit() && value < ASCII_LIMIT || char in UNRESERVED) {
                    append(char)
                } else {
                    append('%').append(HEX[value shr NIBBLE_SHIFT]).append(HEX[value and NIBBLE_MASK])
                }
            }
        }

        private const val ASCII_LIMIT = 128
    }
}
