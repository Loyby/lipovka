package app.lipovka.compat

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/*
 * The handful of Java library calls the game was written with, for code shared with the iPhone,
 * where there is no Java. Same names and behaviour, so the game reads as it always did.
 */

object Math {
    const val PI: Double = kotlin.math.PI
    fun floorMod(x: Int, y: Int): Int = x.mod(y)
    fun floorMod(x: Long, y: Long): Long = x.mod(y)
    fun floorMod(x: Long, y: Int): Int = x.mod(y.toLong()).toInt()
    fun round(x: Double): Long = if (x.isNaN()) 0 else floor(x + .5).toLong()
    fun round(x: Float): Int = if (x.isNaN()) 0 else floor(x + .5f).toInt()
    fun toRadians(d: Double): Double = d / 180.0 * kotlin.math.PI
    fun toDegrees(r: Double): Double = r * 180.0 / kotlin.math.PI
}

object System {
    fun currentTimeMillis(): Long = nowMillis()
    fun nanoTime(): Long = monotonicNanos()
    fun arraycopy(src: IntArray, srcPos: Int, dest: IntArray, destPos: Int, length: Int) {
        src.copyInto(dest, destPos, srcPos, srcPos + length)
    }
}

expect fun nowMillis(): Long
expect fun monotonicNanos(): Long

/** A language, as far as the game cares: Russian or not. */
class Locale(val language: String) {
    override fun equals(other: Any?) = other is Locale && other.language == language
    override fun hashCode() = language.hashCode()
    override fun toString() = language

    companion object {
        val US = Locale("en")
        val ROOT = Locale("")
    }
}

/** Seeded random numbers with the java.util.Random calls the art and the sounds use. */
class Random(seed: Long) {
    private val r = kotlin.random.Random(seed)
    private var spare = Double.NaN

    fun nextInt(): Int = r.nextInt()
    fun nextInt(bound: Int): Int = r.nextInt(bound)
    fun nextFloat(): Float = r.nextFloat()
    fun nextDouble(): Double = r.nextDouble()
    fun nextBoolean(): Boolean = r.nextBoolean()
    fun nextLong(): Long = r.nextLong()

    fun nextGaussian(): Double {
        if (!spare.isNaN()) return spare.also { spare = Double.NaN }
        while (true) {
            val u = 2 * r.nextDouble() - 1
            val v = 2 * r.nextDouble() - 1
            val s = u * u + v * v
            if (s >= 1 || s == 0.0) continue
            val m = sqrt(-2 * ln(s) / s)
            spare = v * m
            return u * m
        }
    }
}

object Log {
    fun w(tag: String, msg: String, t: Throwable? = null) = println("W/$tag: $msg${t?.let { " $it" } ?: ""}")
    fun i(tag: String, msg: String) = println("I/$tag: $msg")
    fun d(tag: String, msg: String) = println("D/$tag: $msg")
}

/** A lock for the few places two threads meet (the sound mixer and the screen). */
expect class Lock() {
    fun lock()
    fun unlock()
}

private val global = Lock()

/** Runs [block] holding one lock shared by everything that calls this; contention here is rare and short. */
inline fun <T> synchronized(@Suppress("UNUSED_PARAMETER") on: Any, block: () -> T): T {
    globalLock().lock()
    try {
        return block()
    } finally {
        globalLock().unlock()
    }
}

@PublishedApi
internal fun globalLock(): Lock = global

fun <K : Comparable<K>, V> Map<K, V>.toSortedMap(): Map<K, V> = entries.sortedBy { it.key }.associate { it.key to it.value }

// ---------------------------------------------------------------- String.format

fun String.format(vararg args: Any?): String = fmt(this, args)
fun String.format(@Suppress("UNUSED_PARAMETER") locale: Locale?, vararg args: Any?): String = fmt(this, args)
fun String.Companion.format(format: String, vararg args: Any?): String = fmt(format, args)
fun String.Companion.format(@Suppress("UNUSED_PARAMETER") locale: Locale?, format: String, vararg args: Any?): String = fmt(format, args)

private val SPEC = Regex("""%(\d+\$)?([-#+ 0,(]*)(\d+)?(\.\d+)?([sdfxXc%n])""")

/**
 * The format strings the game and its string resources use: %s, %d, %f with flags, width and precision,
 * positions (%1$s) and %%. Numbers always come out the English way (a dot, commas for grouping).
 */
fun fmt(format: String, args: Array<out Any?>): String {
    var next = 0
    return SPEC.replace(format) { m ->
        val (pos, flags, width, prec, conv) = m.destructured
        when (conv) {
            "%" -> return@replace "%"
            "n" -> return@replace "\n"
        }
        val i = if (pos.isNotEmpty()) pos.dropLast(1).toInt() - 1 else next++
        val a = args.getOrNull(i)
        val p = prec.drop(1).toIntOrNull()
        var s = when (conv) {
            "d" -> {
                val n = (a as? Number)?.toLong() ?: a.toString().toLongOrNull() ?: 0L
                val digits = abs(n).toString().let { if (',' in flags) group(it) else it }
                (if (n < 0) "-" else if ('+' in flags) "+" else "") + digits
            }
            "f" -> fixed((a as? Number)?.toDouble() ?: 0.0, p ?: 6, ',' in flags, '+' in flags)
            "x" -> ((a as? Number)?.toLong() ?: 0L).toString(16)
            "X" -> ((a as? Number)?.toLong() ?: 0L).toString(16).uppercase()
            "c" -> a.toString().take(1)
            else -> a.toString().let { if (p != null) it.take(p) else it }
        }
        val w = width.toIntOrNull() ?: 0
        if (s.length < w) {
            s = when {
                '-' in flags -> s.padEnd(w)
                '0' in flags && conv in "dfxX" -> {
                    val sign = s.takeWhile { it == '-' || it == '+' }
                    sign + s.drop(sign.length).padStart(w - sign.length, '0')
                }
                else -> s.padStart(w)
            }
        }
        s
    }
}

private fun group(digits: String): String = digits.reversed().chunked(3).joinToString(",").reversed()

private fun fixed(x: Double, prec: Int, grouping: Boolean, plus: Boolean): String {
    if (x.isNaN()) return "NaN"
    val neg = x < 0
    val scale = 10.0.pow(prec)
    val r = (abs(x) * scale).roundToLong()
    val whole = (r / scale.toLong()).toString().let { if (grouping) group(it) else it }
    val frac = if (prec == 0) "" else "." + (r % scale.toLong()).toString().padStart(prec, '0')
    return (if (neg && r != 0L) "-" else if (plus) "+" else "") + whole + frac
}

/** Percent-encodes a query value, as URLEncoder does for UTF-8 (spaces as '+'). */
fun urlEncode(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xff
        when {
            c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c == '.'.code || c == '-'.code || c == '*'.code || c == '_'.code -> append(c.toChar())
            c == ' '.code -> append('+')
            else -> { append('%'); append("0123456789ABCDEF"[c shr 4]); append("0123456789ABCDEF"[c and 15]) }
        }
    }
}
