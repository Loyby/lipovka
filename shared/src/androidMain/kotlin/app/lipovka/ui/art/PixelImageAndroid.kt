package app.lipovka.ui.art

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual class PixelImage actual constructor(private val w: Int, private val h: Int) {
    private val bitmap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    actual val image: ImageBitmap = bitmap.asImageBitmap()
    actual fun set(pixels: IntArray) = bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
}

/** For the widget, which draws with Android's own bitmaps. */
fun Buf.toBitmap(): Bitmap = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888).also { it.setPixels(d, 0, sw, 0, 0, sw, sh) }
