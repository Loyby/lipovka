package app.lipovka.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.lipovka.shared.R

actual fun pixelFont(): FontFamily = FontFamily(Font(R.font.m6x11, FontWeight.Normal))

internal actual fun screenPixelsPerSp(): Float = android.content.res.Resources.getSystem().let { it.displayMetrics.density * it.configuration.fontScale }
