package app.lipovka.time.temporal

import app.lipovka.compat.Locale
import app.lipovka.time.DayOfWeek

/** The first day of the week where the reader lives: Monday in Russian, Sunday in English. */
class WeekFields private constructor(val firstDayOfWeek: DayOfWeek) {
    companion object {
        fun of(locale: Locale): WeekFields = WeekFields(if (locale.language == "en") DayOfWeek.SUNDAY else DayOfWeek.MONDAY)
    }
}
