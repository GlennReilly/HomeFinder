package au.homefinder.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost

fun main(): Unit = application {
    Window(onCloseRequest = ::exitApplication, title = appTitle()) {
        ProvideMapPresentationHost(host = rememberAwtComposeMapPresentationHost(window)) {
            App()
        }
    }
}
