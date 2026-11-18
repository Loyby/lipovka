package app.lipovka

import androidx.compose.ui.window.ComposeUIViewController
import app.lipovka.ui.LipovkaApp
import platform.UIKit.UIViewController

/** The app's one set of books, made when the app first draws. */
object Lipovka {
    val container: AppContainer by lazy { AppContainer(IosPlatform()) }
}

/** What the Swift app shows: every screen, drawn by Compose. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController { LipovkaApp(Lipovka.container) }
