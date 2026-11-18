package app.lipovka.compat

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.Foundation.timeIntervalSince1970
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time
import platform.posix.memcpy

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun monotonicNanos(): Long = (NSProcessInfo.processInfo.systemUptime * 1e9).toLong()

actual class Lock actual constructor() {
    private val l = NSRecursiveLock()
    actual fun lock() = l.lock()
    actual fun unlock() = l.unlock()
}

/** An NSData's bytes as a ByteArray. */
fun NSData.toByteArray(): ByteArray {
    val n = length.toInt()
    val out = ByteArray(n)
    if (n > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

actual fun httpGet(url: String, userAgent: String): String {
    val u = NSURL.URLWithString(url) ?: error("bad url")
    val req = NSMutableURLRequest.requestWithURL(u)
    req.setValue(userAgent, forHTTPHeaderField = "User-Agent")
    req.timeoutInterval = 8.0
    val done = dispatch_semaphore_create(0)
    var body: ByteArray? = null
    var failure: String? = null
    NSURLSession.sharedSession.dataTaskWithRequest(req) { data, response, error ->
        val code = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
        when {
            error != null -> failure = error.localizedDescription
            code !in 200..299 -> failure = "HTTP $code"
            else -> body = data?.toByteArray() ?: ByteArray(0)
        }
        dispatch_semaphore_signal(done)
    }.resume()
    dispatch_semaphore_wait(done, dispatch_time(DISPATCH_TIME_NOW, 15_000_000_000L))
    return body?.decodeToString() ?: error(failure ?: "timed out")
}
