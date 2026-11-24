package app.lipovka.ui.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.lipovka.ui.Ctx
import app.lipovka.top
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage

internal actual fun decodeCardImage(bytes: ByteArray): ImageBitmap =
    Image.makeFromEncoded(bytes).toComposeImageBitmap()

internal actual fun sendCard(ctx: Ctx, bitmap: ImageBitmap, caption: String) {
    val png = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData()?.bytes ?: return
    val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()) }
    val image = UIImage.imageWithData(data) ?: return
    val controller = UIActivityViewController(activityItems = listOf(image, caption), applicationActivities = null)
    val presenter = top() ?: return
    controller.popoverPresentationController?.sourceView = presenter.view
    presenter.presentViewController(controller, animated = true, completion = null)
}
