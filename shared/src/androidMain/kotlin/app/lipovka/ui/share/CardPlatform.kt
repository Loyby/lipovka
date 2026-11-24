package app.lipovka.ui.share

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import app.lipovka.android
import app.lipovka.ui.Ctx
import app.lipovka.ui.R
import java.io.File

internal actual fun decodeCardImage(bytes: ByteArray): ImageBitmap =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()

internal actual fun sendCard(ctx: Ctx, bitmap: ImageBitmap, caption: String) {
    val context = ctx.container.android.context
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File.createTempFile("lipovka-", ".png", dir)
    file.outputStream().use { bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
    val intent = Intent(Intent.ACTION_SEND).setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, caption)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, ctx.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
