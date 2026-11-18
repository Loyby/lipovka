package app.lipovka.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import platform.Foundation.NSProcessInfo

actual suspend fun <R> PostDb.tx(block: suspend () -> R): R = useWriterConnection { it.immediateTransaction { block() } }

actual fun elapsedRealtime(): Long = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
