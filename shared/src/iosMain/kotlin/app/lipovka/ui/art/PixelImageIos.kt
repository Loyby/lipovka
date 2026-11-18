package app.lipovka.ui.art

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

actual class PixelImage actual constructor(private val w: Int, private val h: Int) {
    private val bytes = ByteArray(w * h * 4)
    private var current: ImageBitmap = ImageBitmap(w, h)

    actual val image: ImageBitmap get() = current

    /** A fresh image for each frame: Skia keeps no live link to the pixels it was made from. */
    actual fun set(pixels: IntArray) {
        var o = 0
        for (c in pixels) {
            bytes[o] = c.toByte()
            bytes[o + 1] = (c shr 8).toByte()
            bytes[o + 2] = (c shr 16).toByte()
            bytes[o + 3] = (c ushr 24).toByte()
            o += 4
        }
        current = Image.makeRaster(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL), bytes, w * 4).toComposeImageBitmap()
    }
}
