package app.lipovka.ui.art

import app.lipovka.IosPlatform
import kotlinx.cinterop.*
import platform.zlib.Z_OK
import platform.zlib.uncompress

internal actual fun artResource(path: String): ByteArray? = IosPlatform.bundleFile(path)

internal actual fun inflateIndices(bytes: ByteArray, size: Int): ByteArray = memScoped {
    val output = ByteArray(size)
    val length = alloc<ULongVar>()
    length.value = size.toULong()
    output.usePinned { dst ->
        bytes.usePinned { src ->
            check(uncompress(dst.addressOf(0).reinterpret(), length.ptr,
                src.addressOf(0).reinterpret(), bytes.size.toULong()) == Z_OK)
        }
    }
    check(length.value == size.toULong())
    output
}
