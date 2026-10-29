package au.homefinder.app

import androidx.compose.runtime.Composable

/** The system Back action: Android's Back button or gesture. Desktop has none; Escape is handled in [App]. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
