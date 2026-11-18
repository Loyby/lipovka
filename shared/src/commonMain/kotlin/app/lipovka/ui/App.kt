package app.lipovka.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lipovka.AppContainer
import app.lipovka.data.Settings
import app.lipovka.ui.theme.LipovkaTheme

/** The whole app on screen, the same on both phones. */
@Composable
fun LipovkaApp(container: AppContainer) {
    val settings: Settings? by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val ru = container.platform.ru
    val ctx = remember(container, ru) { Ctx(container, ru) }
    // Wait for settings so the welcome pages don't flash for someone already here.
    settings?.let { s ->
        CompositionLocalProvider(LocalContext provides ctx) {
            LipovkaTheme { LipovkaRoot(s) }
        }
    }
}
