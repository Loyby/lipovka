package app.lipovka.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import app.lipovka.IosPlatform

actual fun pixelFont(): FontFamily {
    val bytes = IosPlatform.bundleFile("m6x11.ttf") ?: return FontFamily.Default
    return FontFamily(Font(identity = "m6x11", data = bytes, weight = FontWeight.Normal))
}

internal actual fun screenPixelsPerSp(): Float = platform.UIKit.UIScreen.mainScreen.scale.toFloat()
