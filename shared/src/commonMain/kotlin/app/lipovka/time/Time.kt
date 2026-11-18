package app.lipovka.time

import app.lipovka.compat.Locale
import app.lipovka.time.format.DateTimeFormatter
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Instant as KInstant
import kotlinx.datetime.LocalDateTime as KLocalDateTime

/*
 * The part of java.time the game uses, for code shared with the iPhone: the same names and the same
 * behaviour (ISO calendar, epoch days, the phone's time zone), so the game reads as it always did.
 * Time zones and "now" come from kotlinx-datetime.
 */

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

    /** 1 for Monday … 7 for Sunday. */
    val value: Int get() = ordinal + 1

    fun plus(days: Long): DayOfWeek = entries[((ordinal + days) % 7 + 7).toInt() % 7]
    fun minus(days: Long): DayOfWeek = plus(-days)

    fun getDisplayName(style: app.lipovka.time.format.TextStyle, locale: Locale): String = Names.day(this, style, locale)

    companion object {
        fun of(n: Int): DayOfWeek = entries[n - 1]
    }
}

enum class Month {
    JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER;

    val value: Int get() = ordinal + 1

    fun getDisplayName(style: app.lipovka.time.format.TextStyle, locale: Locale): String = Names.month(value, style, locale)

    companion object {
        fun of(n: Int): Month = entries[n - 1]
    }
}

private fun leap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
private fun monthLength(y: Int, m: Int) = when (m) {
    2 -> if (leap(y)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

/** A date with no time or zone, as java.time.LocalDate. */
class LocalDate private constructor(private val epochDay: Long, val year: Int, val monthValue: Int, val dayOfMonth: Int) : Comparable<LocalDate> {
    val month: Month get() = Month.of(monthValue)
    val dayOfWeek: DayOfWeek get() = DayOfWeek.entries[((epochDay + 3) % 7 + 7).toInt() % 7]
    val dayOfYear: Int get() = (epochDay - of(year, 1, 1).epochDay).toInt() + 1
    val isLeapYear: Boolean get() = leap(year)

    fun isLeapYear(): Boolean = leap(year)
    fun lengthOfMonth(): Int = monthLength(year, monthValue)
    fun lengthOfYear(): Int = if (leap(year)) 366 else 365
    fun toEpochDay(): Long = epochDay

    fun plusDays(n: Long): LocalDate = ofEpochDay(epochDay + n)
    fun plusDays(n: Int): LocalDate = plusDays(n.toLong())
    fun minusDays(n: Long): LocalDate = ofEpochDay(epochDay - n)
    fun minusDays(n: Int): LocalDate = minusDays(n.toLong())
    fun plusWeeks(n: Long): LocalDate = plusDays(n * 7)
    fun minusWeeks(n: Long): LocalDate = minusDays(n * 7)
    fun plusMonths(n: Long): LocalDate {
        val total = year * 12L + (monthValue - 1) + n
        val y = Math.floorDiv(total, 12L).toInt()
        val m = Math.floorMod(total, 12L).toInt() + 1
        return of(y, m, minOf(dayOfMonth, monthLength(y, m)))
    }
    fun plusMonths(n: Int): LocalDate = plusMonths(n.toLong())
    fun minusMonths(n: Long): LocalDate = plusMonths(-n)
    fun minusMonths(n: Int): LocalDate = plusMonths(-n.toLong())
    fun plusYears(n: Long): LocalDate = plusMonths(n * 12)
    fun plusYears(n: Int): LocalDate = plusYears(n.toLong())
    fun minusYears(n: Long): LocalDate = plusMonths(-n * 12)
    fun minusYears(n: Int): LocalDate = minusYears(n.toLong())
    fun withDayOfMonth(d: Int): LocalDate = of(year, monthValue, d)
    fun withDayOfYear(d: Int): LocalDate = of(year, 1, 1).plusDays((d - 1).toLong())
    fun withMonth(m: Int): LocalDate = of(year, m, minOf(dayOfMonth, monthLength(year, m)))
    fun withYear(y: Int): LocalDate = of(y, monthValue, minOf(dayOfMonth, monthLength(y, monthValue)))

    fun isBefore(o: LocalDate) = epochDay < o.epochDay
    fun isAfter(o: LocalDate) = epochDay > o.epochDay
    fun isEqual(o: LocalDate) = epochDay == o.epochDay

    fun atStartOfDay(): LocalDateTime = LocalDateTime(this, LocalTime.MIDNIGHT)
    fun atStartOfDay(zone: ZoneId): ZonedDateTime = ZonedDateTime(atStartOfDay(), zone)
    fun atTime(hour: Int, minute: Int): LocalDateTime = LocalDateTime(this, LocalTime.of(hour, minute))
    fun atTime(hour: Int, minute: Int, second: Int): LocalDateTime = LocalDateTime(this, LocalTime.of(hour, minute, second))
    fun atTime(t: LocalTime): LocalDateTime = LocalDateTime(this, t)

    fun format(f: DateTimeFormatter): String = f.format(this, null)

    override fun compareTo(other: LocalDate) = epochDay.compareTo(other.epochDay)
    override fun equals(other: Any?) = other is LocalDate && other.epochDay == epochDay
    override fun hashCode() = epochDay.hashCode()
    override fun toString() = "${year.toString().padStart(4, '0')}-${monthValue.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}"

    companion object {
        fun of(year: Int, month: Int, day: Int): LocalDate {
            require(month in 1..12 && day in 1..monthLength(year, month)) { "invalid date $year-$month-$day" }
            // days from civil (Howard Hinnant's algorithm)
            val y = if (month <= 2) year - 1L else year.toLong()
            val era = Math.floorDiv(y, 400L)
            val yoe = y - era * 400
            val mp = (month + 9) % 12
            val doy = (153 * mp + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return LocalDate(era * 146097 + doe - 719468, year, month, day)
        }

        fun of(year: Int, month: Month, day: Int) = of(year, month.value, day)

        fun ofEpochDay(n: Long): LocalDate {
            val z = n + 719468
            val era = Math.floorDiv(z, 146097L)
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
            return LocalDate(n, y, m, d)
        }

        fun ofEpochDay(n: Int) = ofEpochDay(n.toLong())

        fun ofYearDay(year: Int, dayOfYear: Int): LocalDate = of(year, 1, 1).plusDays((dayOfYear - 1).toLong())

        fun now(): LocalDate = LocalDateTime.now().toLocalDate()
        fun now(zone: ZoneId): LocalDate = LocalDateTime.now(zone).toLocalDate()

        fun parse(s: String): LocalDate {
            val (y, m, d) = s.trim().split('-').map { it.toInt() }
            return of(y, m, d)
        }

        val MIN: LocalDate = ofEpochDay(-365243219162L)
        val MAX: LocalDate = ofEpochDay(365241780471L)
        val EPOCH: LocalDate = ofEpochDay(0)
    }
}

/** A time of day, as java.time.LocalTime. */
class LocalTime private constructor(val hour: Int, val minute: Int, val second: Int, val nano: Int = 0) : Comparable<LocalTime> {
    fun toSecondOfDay(): Int = hour * 3600 + minute * 60 + second
    fun isBefore(o: LocalTime) = this < o
    fun isAfter(o: LocalTime) = this > o
    fun plusMinutes(n: Long) = ofSecondOfDay(Math.floorMod(toSecondOfDay() + n * 60, 86400L))
    fun plusHours(n: Long) = plusMinutes(n * 60)
    fun withHour(h: Int) = of(h, minute, second)
    fun withMinute(m: Int) = of(hour, m, second)

    override fun compareTo(other: LocalTime) = (toSecondOfDay() * 1_000_000_000L + nano).compareTo(other.toSecondOfDay() * 1_000_000_000L + other.nano)
    override fun equals(other: Any?) = other is LocalTime && other.compareTo(this) == 0
    override fun hashCode() = toSecondOfDay() * 31 + nano
    override fun toString() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}" +
        if (second != 0) ":${second.toString().padStart(2, '0')}" else ""

    companion object {
        val MIDNIGHT = LocalTime(0, 0, 0)
        val NOON = LocalTime(12, 0, 0)
        fun of(hour: Int, minute: Int, second: Int = 0, nano: Int = 0): LocalTime {
            require(hour in 0..23 && minute in 0..59 && second in 0..59) { "invalid time $hour:$minute:$second" }
            return LocalTime(hour, minute, second, nano)
        }
        fun ofSecondOfDay(s: Long): LocalTime = LocalTime((s / 3600).toInt(), (s / 60 % 60).toInt(), (s % 60).toInt())
        fun now(): LocalTime = LocalDateTime.now().toLocalTime()
        fun now(zone: ZoneId): LocalTime = LocalDateTime.now(zone).toLocalTime()
    }
}

/** A date and time with no zone, as java.time.LocalDateTime. */
class LocalDateTime internal constructor(private val date: LocalDate, private val time: LocalTime) : Comparable<LocalDateTime> {
    val year get() = date.year
    val monthValue get() = date.monthValue
    val month get() = date.month
    val dayOfMonth get() = date.dayOfMonth
    val dayOfWeek get() = date.dayOfWeek
    val dayOfYear get() = date.dayOfYear
    val hour get() = time.hour
    val minute get() = time.minute
    val second get() = time.second
    val nano get() = time.nano

    fun toLocalDate(): LocalDate = date
    fun toLocalTime(): LocalTime = time

    private fun plusSecondsExact(n: Long): LocalDateTime {
        val total = time.toSecondOfDay() + n
        val days = Math.floorDiv(total, 86400L)
        return LocalDateTime(date.plusDays(days), LocalTime.ofSecondOfDay(Math.floorMod(total, 86400L)).let { LocalTime.of(it.hour, it.minute, it.second, time.nano) })
    }

    fun plusSeconds(n: Long) = plusSecondsExact(n)
    fun plusMinutes(n: Long) = plusSecondsExact(n * 60)
    fun plusMinutes(n: Int) = plusMinutes(n.toLong())
    fun plusHours(n: Long) = plusSecondsExact(n * 3600)
    fun plusHours(n: Int) = plusHours(n.toLong())
    fun plusDays(n: Long) = LocalDateTime(date.plusDays(n), time)
    fun plusDays(n: Int) = plusDays(n.toLong())
    fun plusMonths(n: Long) = LocalDateTime(date.plusMonths(n), time)
    fun plusYears(n: Long) = LocalDateTime(date.plusYears(n), time)
    fun minusSeconds(n: Long) = plusSecondsExact(-n)
    fun minusMinutes(n: Long) = plusSecondsExact(-n * 60)
    fun minusMinutes(n: Int) = minusMinutes(n.toLong())
    fun minusHours(n: Long) = plusSecondsExact(-n * 3600)
    fun minusHours(n: Int) = minusHours(n.toLong())
    fun minusDays(n: Long) = LocalDateTime(date.minusDays(n), time)
    fun minusDays(n: Int) = minusDays(n.toLong())
    fun withHour(h: Int) = LocalDateTime(date, LocalTime.of(h, time.minute, time.second))
    fun withMinute(m: Int) = LocalDateTime(date, LocalTime.of(time.hour, m, time.second))
    fun withSecond(s: Int) = LocalDateTime(date, LocalTime.of(time.hour, time.minute, s))
    fun withNano(n: Int) = LocalDateTime(date, LocalTime.of(time.hour, time.minute, time.second, n))
    fun withDayOfMonth(d: Int) = LocalDateTime(date.withDayOfMonth(d), time)

    fun isBefore(o: LocalDateTime) = this < o
    fun isAfter(o: LocalDateTime) = this > o
    fun isEqual(o: LocalDateTime) = compareTo(o) == 0

    fun atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime(this, zone)
    fun format(f: DateTimeFormatter): String = f.format(date, time)

    internal fun toK(): KLocalDateTime = KLocalDateTime(year, monthValue, dayOfMonth, hour, minute, second, nano)

    override fun compareTo(other: LocalDateTime): Int = date.compareTo(other.date).takeIf { it != 0 } ?: time.compareTo(other.time)
    override fun equals(other: Any?) = other is LocalDateTime && other.date == date && other.time == time
    override fun hashCode() = date.hashCode() * 31 + time.hashCode()
    override fun toString() = "${date}T$time"

    companion object {
        fun of(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): LocalDateTime =
            LocalDateTime(LocalDate.of(year, month, day), LocalTime.of(hour, minute, second))
        fun of(date: LocalDate, time: LocalTime) = LocalDateTime(date, time)
        fun now(): LocalDateTime = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): LocalDateTime = Instant.now().atZone(zone).toLocalDateTime()
        internal fun fromK(k: KLocalDateTime) = LocalDateTime(LocalDate.of(k.year, k.monthNumber, k.dayOfMonth), LocalTime.of(k.hour, k.minute, k.second, k.nanosecond))
    }
}

/** A time zone, as java.time.ZoneId. */
class ZoneId private constructor(internal val tz: TimeZone) {
    val id: String get() = tz.id
    val rules: ZoneRules get() = ZoneRules(tz)
    override fun equals(other: Any?) = other is ZoneId && other.tz == tz
    override fun hashCode() = tz.hashCode()
    override fun toString() = tz.id

    companion object {
        fun systemDefault(): ZoneId = ZoneId(TimeZone.currentSystemDefault())
        fun of(id: String): ZoneId = ZoneId(TimeZone.of(id))
        val UTC: ZoneId = ZoneId(TimeZone.UTC)
    }
}

/** The offsets of a time zone, as java.time.zone.ZoneRules. */
class ZoneRules internal constructor(private val tz: TimeZone) {
    fun getOffset(i: Instant): ZoneOffset = ZoneOffset(tz.offsetAt(KInstant.fromEpochMilliseconds(i.toEpochMilli())).totalSeconds)
}

/** An offset from UTC, as java.time.ZoneOffset. */
class ZoneOffset(val totalSeconds: Int)

/** A moment, as java.time.Instant (millisecond precision is all the game uses). */
class Instant private constructor(private val ms: Long) : Comparable<Instant> {
    fun toEpochMilli(): Long = ms
    val epochSecond: Long get() = Math.floorDiv(ms, 1000L)
    fun atZone(zone: ZoneId): ZonedDateTime =
        ZonedDateTime(LocalDateTime.fromK(KInstant.fromEpochMilliseconds(ms).toLocalDateTime(zone.tz)), zone)
    fun plusMillis(n: Long) = Instant(ms + n)
    fun plusSeconds(n: Long) = Instant(ms + n * 1000)
    fun isBefore(o: Instant) = ms < o.ms
    fun isAfter(o: Instant) = ms > o.ms

    override fun compareTo(other: Instant) = ms.compareTo(other.ms)
    override fun equals(other: Any?) = other is Instant && other.ms == ms
    override fun hashCode() = ms.hashCode()
    override fun toString() = KInstant.fromEpochMilliseconds(ms).toString()

    companion object {
        fun ofEpochMilli(ms: Long) = Instant(ms)
        fun ofEpochSecond(s: Long) = Instant(s * 1000)
        fun now() = Instant(Clock.System.now().toEpochMilliseconds())
    }
}

/** A date and time in a zone, as java.time.ZonedDateTime. */
class ZonedDateTime internal constructor(private val local: LocalDateTime, val zone: ZoneId) {
    val year get() = local.year
    val monthValue get() = local.monthValue
    val month get() = local.month
    val dayOfMonth get() = local.dayOfMonth
    val dayOfWeek get() = local.dayOfWeek
    val dayOfYear get() = local.dayOfYear
    val hour get() = local.hour
    val minute get() = local.minute
    val second get() = local.second

    fun toLocalDateTime(): LocalDateTime = local
    fun toLocalDate(): LocalDate = local.toLocalDate()
    fun toLocalTime(): LocalTime = local.toLocalTime()
    fun toInstant(): Instant = Instant.ofEpochMilli(local.toK().toInstant(zone.tz).toEpochMilliseconds())
    fun toEpochSecond(): Long = toInstant().epochSecond
    fun plusDays(n: Long) = ZonedDateTime(local.plusDays(n), zone)
    fun minusDays(n: Long) = ZonedDateTime(local.minusDays(n), zone)
    fun plusHours(n: Long) = toInstant().plusSeconds(n * 3600).atZone(zone)
    fun plusMinutes(n: Long) = toInstant().plusSeconds(n * 60).atZone(zone)
    fun format(f: DateTimeFormatter): String = local.format(f)

    override fun equals(other: Any?) = other is ZonedDateTime && other.local == local && other.zone == zone
    override fun hashCode() = local.hashCode()
    override fun toString() = "$local[$zone]"
}

/** A month of a year, as java.time.YearMonth. */
class YearMonth private constructor(val year: Int, val monthValue: Int) : Comparable<YearMonth> {
    val month: Month get() = Month.of(monthValue)
    fun atDay(d: Int): LocalDate = LocalDate.of(year, monthValue, d)
    fun atEndOfMonth(): LocalDate = atDay(lengthOfMonth())
    fun lengthOfMonth(): Int = monthLength(year, monthValue)
    fun plusMonths(n: Long): YearMonth = from(atDay(1).plusMonths(n))
    fun plusMonths(n: Int): YearMonth = plusMonths(n.toLong())
    fun minusMonths(n: Long): YearMonth = plusMonths(-n)
    fun minusMonths(n: Int): YearMonth = plusMonths(-n.toLong())
    fun isBefore(o: YearMonth) = this < o
    fun isAfter(o: YearMonth) = this > o
    fun format(f: DateTimeFormatter): String = f.format(atDay(1), null)

    override fun compareTo(other: YearMonth) = (year * 12 + monthValue).compareTo(other.year * 12 + other.monthValue)
    override fun equals(other: Any?) = other is YearMonth && other.year == year && other.monthValue == monthValue
    override fun hashCode() = year * 12 + monthValue
    override fun toString() = "$year-${monthValue.toString().padStart(2, '0')}"

    companion object {
        fun of(year: Int, month: Int) = YearMonth(year, month)
        fun from(d: LocalDate) = YearMonth(d.year, d.monthValue)
        fun now() = from(LocalDate.now())
    }
}

private object Math {
    fun floorDiv(a: Long, b: Long): Long = a.floorDiv(b)
    fun floorMod(a: Long, b: Long): Long = a.mod(b)
}

/** Day and month names in English and Russian, the way java.time prints them. */
internal object Names {
    private val daysEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val daysRu = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
    private val daysRuShort = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    private val monthsEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val monthsRu = listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")
    private val monthsRuOf = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    private val monthsRuShort = listOf("янв.", "февр.", "мар.", "апр.", "мая", "июн.", "июл.", "авг.", "сент.", "окт.", "нояб.", "дек.")
    private val monthsRuShortStandalone = listOf("янв.", "февр.", "март", "апр.", "май", "июнь", "июль", "авг.", "сент.", "окт.", "нояб.", "дек.")

    fun ru(l: Locale?) = l?.language == "ru"

    fun day(d: DayOfWeek, style: app.lipovka.time.format.TextStyle, l: Locale?): String {
        val i = d.ordinal
        return if (ru(l)) {
            if (style.short) daysRuShort[i] else daysRu[i]
        } else {
            if (style.short) daysEn[i].take(3) else daysEn[i]
        }
    }

    /** [standalone]: the month on its own ("ноябрь"); otherwise as in a date ("18 ноября"). */
    fun month(m: Int, style: app.lipovka.time.format.TextStyle, l: Locale?): String {
        val i = m - 1
        return if (ru(l)) when {
            style.short && style.standalone -> monthsRuShortStandalone[i]
            style.short -> monthsRuShort[i]
            style.standalone -> monthsRu[i]
            else -> monthsRuOf[i]
        } else if (style.short) monthsEn[i].take(3) else monthsEn[i]
    }
}
