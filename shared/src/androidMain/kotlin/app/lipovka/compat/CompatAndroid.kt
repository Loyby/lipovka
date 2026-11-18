package app.lipovka.compat

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.locks.ReentrantLock

actual fun nowMillis(): Long = java.lang.System.currentTimeMillis()

actual fun monotonicNanos(): Long = java.lang.System.nanoTime()

actual class Lock actual constructor() {
    private val l = ReentrantLock()
    actual fun lock() = l.lock()
    actual fun unlock() = l.unlock()
}

actual fun httpGet(url: String, userAgent: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 6000
    c.readTimeout = 8000
    c.setRequestProperty("User-Agent", userAgent)
    try {
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}
