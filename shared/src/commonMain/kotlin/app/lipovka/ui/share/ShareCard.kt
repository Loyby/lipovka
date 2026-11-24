package app.lipovka.ui.share

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import app.lipovka.ui.Ctx
import app.lipovka.ui.R
import app.lipovka.ui.art.Scene
import app.lipovka.ui.art.artResource
import app.lipovka.ui.art.toImage
import app.lipovka.ui.theme.C
import app.lipovka.ui.theme.Pixel

/**
 * A picture to post: a scene from the village, a title, a few numbers or a few lines of a letter, and at the
 * bottom the game's name with a small QR code of its Google Play page.
 */
class Card(
    val title: String,
    val sub: String,
    /** Big number and what it counts, two to a row. */
    val stats: List<Pair<String, String>> = emptyList(),
    /** Words in place of numbers: a letter's lines. */
    val quote: String? = null,
    val scene: Scene? = null,
    /** The rows of the scene shown, in art pixels: the interesting band of it. */
    val rows: IntRange = 0..0,
    /** A drawn illustration (assets/cards, by tools/card_art.py) in place of a scene. */
    val art: String? = null,
    /** One thing held up large on paper in place of a scene: a stamp, a gift. */
    val picture: app.lipovka.ui.art.Buf? = null,
)

internal expect fun decodeCardImage(bytes: ByteArray): ImageBitmap
internal expect fun sendCard(ctx: Ctx, bitmap: ImageBitmap, caption: String)

object ShareCard {
    const val W = 1080
    const val H = 1350
    private const val M = 60
    private const val PLAY = "https://play.google.com/store/apps/details?id=app.lipovka"
    private val qr by lazy { artResource("qr_play.txt")!!.decodeToString().lineSequence().filter { it.isNotBlank() }.toList() }

    fun send(ctx: Ctx, bitmap: ImageBitmap) = sendCard(ctx, bitmap, ctx.getString(R.string.share_text, PLAY))

    fun render(ctx: Ctx, card: Card, text: TextMeasurer): ImageBitmap {
        val out = ImageBitmap(W, H)
        val density = Density(1f)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(out), Size(W.toFloat(), H.toFloat())) {
            fun box(x: Int, y: Int, w: Int, h: Int, color: Color) =
                drawRect(color, Offset(x.toFloat(), y.toFloat()), Size(w.toFloat(), h.toFloat()))
            fun label(value: String, x: Int, y: Int, width: Int, size: Int, color: Color, lines: Int = 1) {
                val layout = text.measure(value, TextStyle(color = color, fontFamily = Pixel, fontSize = size.sp,
                    lineHeight = (size + 2).sp), maxLines = lines, overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = width), density = density)
                drawText(layout, topLeft = Offset(x.toFloat(), y.toFloat()))
            }
            drawRect(C.paper)
            box(24, 24, W - 48, H - 48, C.ink)
            box(32, 32, W - 64, H - 64, C.paper)
            val art = card.art?.let { ctx.container.platform.asset("cards/$it.webp") }?.let(::decodeCardImage)
            val pic = card.picture?.toImage()
            val scene = card.scene
            val image = pic ?: art ?: scene?.frame(9)?.toImage()
            if (image != null) {
                val crop = scene != null && art == null && pic == null
                val unit = if (scene != null) image.width / scene.w else 1
                val sy = if (crop) card.rows.first * unit else 0
                val sh = if (crop) (card.rows.last - card.rows.first + 1) * unit else image.height
                val panelH = 620
                val panelW = W - 2 * M
                val scale = if (pic != null) minOf((panelW - 120) / image.width, (panelH - 120) / sh).coerceAtLeast(1).toFloat()
                    else minOf(panelW.toFloat() / image.width, panelH.toFloat() / sh)
                val dw = (image.width * scale).toInt()
                val dh = (sh * scale).toInt()
                box(M - 8, M - 8, panelW + 16, panelH + 16, C.ink)
                box(M, M, panelW, panelH, C.paper2)
                drawImage(image, IntOffset(0, sy), IntSize(image.width, sh),
                    IntOffset((W - dw) / 2, M + (panelH - dh) / 2), IntSize(dw, dh),
                    filterQuality = if (art != null) FilterQuality.Medium else FilterQuality.None)
            }
            label(card.title, M, 690, 960, 76, C.ink)
            label(card.sub, M, 770, 960, 40, C.muted)
            if (card.quote != null) {
                box(M, 832, 960, 258, C.light)
                label(card.quote, M + 24, 852, 912, 44, C.ink, 5)
            } else {
                card.stats.take(4).forEachIndexed { i, (value, name) ->
                    val x = M + i % 2 * 488
                    val y = 832 + i / 2 * 130
                    box(x, y, 472, 114, C.ink)
                    box(x + 6, y + 6, 460, 96, C.paper2)
                    label(value, x + 22, y + 4, 428, 60, C.ink)
                    label(name, x + 22, y + 65, 428, 34, C.muted)
                }
            }
            val side = (qr.size + 8) * 5
            val x = W - M - side
            val y = H - M - side
            box(x, y, side, side, Color.White)
            qr.forEachIndexed { row, bits -> bits.forEachIndexed { col, bit ->
                if (bit == '1') box(x + (col + 4) * 5, y + (row + 4) * 5, 5, 5, C.ink)
            } }
            label(ctx.getString(R.string.app_name), M, 1140, x - M - 12, 60, C.red)
            label(ctx.getString(R.string.share_tagline), M, 1212, x - M - 12, 40, C.muted)
            label("Google Play", M, 1260, x - M - 12, 36, C.blue)
        }
        return out
    }
}
