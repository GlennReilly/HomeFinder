package au.homefinder.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import au.homefinder.app.api.ApiConfig
import au.homefinder.shared.api.ApiContract
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost

private const val API_URL_VARIABLE = "HOMEFINDER_API_URL"
private const val API_KEY_VARIABLE = "HOMEFINDER_API_KEY"

/** The desktop app reaches the API at HOMEFINDER_API_URL (default: this machine) with HOMEFINDER_API_KEY. */
fun desktopApiConfig(env: Map<String, String> = System.getenv()): ApiConfig = ApiConfig(
    baseUrl = env[API_URL_VARIABLE]?.takeIf { it.isNotBlank() } ?: "http://localhost:${ApiContract.DEFAULT_PORT}",
    apiKey = env[API_KEY_VARIABLE].orEmpty(),
)

fun main(): Unit = application {
    Window(onCloseRequest = ::exitApplication, title = appTitle()) {
        ProvideMapPresentationHost(host = rememberAwtComposeMapPresentationHost(window)) {
            App(desktopApiConfig())
        }
    }
}
