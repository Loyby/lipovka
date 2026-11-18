package app.lipovka.data

import android.os.SystemClock
import androidx.room.withTransaction

actual suspend fun <R> PostDb.tx(block: suspend () -> R): R = withTransaction { block() }

actual fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

/** A database in memory, for tests. */
fun PostDb.Companion.memory(context: android.content.Context): PostDb =
    androidx.room.Room.inMemoryDatabaseBuilder<PostDb>(context).build()
