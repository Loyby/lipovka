package app.lipovka.time.format

import app.lipovka.compat.Locale
import app.lipovka.time.LocalDate
import app.lipovka.time.LocalTime
import app.lipovka.time.Names

enum class TextStyle(internal val short: Boolean, internal val standalone: Boolean) {
    FULL(false, false), FULL_STANDALONE(false, true), SHORT(true, false), SHORT_STANDALONE(true, true),
    NARROW(true, false), NARROW_STANDALONE(true, true),
}

/**
 * The date patterns the game prints, as java.time.format.DateTimeFormatter.ofPattern: d, dd, M, MM, MMM, MMMM
 * (a month inside a date, "18 ноября"), LLL, LLLL (a month on its own, "ноябрь"), E…EEEE, y, yy, yyyy, H, HH, m, mm
 * and quoted text.
 */
class DateTimeFormatter private constructor(private val pattern: String, private val locale: Locale?) {
    fun withLocale(l: Locale) = DateTimeFormatter(pattern, l)

    fun format(d: LocalDate): String = format(d, null)

    internal fun format(d: LocalDate, t: LocalTime?): String = buildString {
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\'') {
                val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length else it }
                if (end == i + 1) append('\'') else append(pattern, i + 1, end)
                i = end + 1
                continue
            }
            if (!c.isLetter()) { append(c); i++; continue }
            var n = 1
            while (i + n < pattern.length && pattern[i + n] == c) n++
            when (c) {
                'd' -> append(d.dayOfMonth.toString().padStart(n, '0'))
                'D' -> append(d.dayOfYear.toString().padStart(n, '0'))
                'M' -> append(
                    when {
                        n >= 4 -> Names.month(d.monthValue, TextStyle.FULL, locale)
                        n == 3 -> Names.month(d.monthValue, TextStyle.SHORT, locale)
                        else -> d.monthValue.toString().padStart(n, '0')
                    },
                )
                'L' -> append(
                    when {
                        n >= 4 -> Names.month(d.monthValue, TextStyle.FULL_STANDALONE, locale)
                        n == 3 -> Names.month(d.monthValue, TextStyle.SHORT_STANDALONE, locale)
                        else -> d.monthValue.toString().padStart(n, '0')
                    },
                )
                'E', 'e', 'c' -> append(Names.day(d.dayOfWeek, if (n >= 4) TextStyle.FULL else TextStyle.SHORT, locale))
                'y', 'u' -> append(if (n == 2) (d.year % 100).toString().padStart(2, '0') else d.year.toString().padStart(n, '0'))
                'H' -> append((t?.hour ?: 0).toString().padStart(n, '0'))
                'm' -> append((t?.minute ?: 0).toString().padStart(n, '0'))
                's' -> append((t?.second ?: 0).toString().padStart(n, '0'))
                else -> repeat(n) { append(c) }
            }
            i += n
        }
    }

    companion object {
        fun ofPattern(pattern: String): DateTimeFormatter = DateTimeFormatter(pattern, null)
        fun ofPattern(pattern: String, locale: Locale): DateTimeFormatter = DateTimeFormatter(pattern, locale)
        val ISO_LOCAL_DATE: DateTimeFormatter = DateTimeFormatter("yyyy-MM-dd", null)
    }
}
