package au.homefinder.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import au.homefinder.android.BuildConfig
import au.homefinder.app.App
import au.homefinder.app.api.ApiConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(ApiConfig(BuildConfig.API_BASE_URL, BuildConfig.API_KEY)) }
    }
}
