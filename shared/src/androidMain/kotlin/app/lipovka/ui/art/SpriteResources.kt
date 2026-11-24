package app.lipovka.ui.art

import java.util.zip.InflaterInputStream

internal actual fun artResource(path: String): ByteArray? =
    Sprites::class.java.getResourceAsStream("/$path")?.use { it.readBytes() }

internal actual fun inflateIndices(bytes: ByteArray, size: Int): ByteArray =
    InflaterInputStream(bytes.inputStream()).use { it.readBytes() }.also { require(it.size == size) }
