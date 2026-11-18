package app.lipovka.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import app.lipovka.AppContainer
import app.lipovka.compat.Locale
import app.lipovka.compat.fmt

/** A string in both languages, named as its Android resource is (see [R]). */
@Immutable
class Sid(val en: String, val ru: String) {
    operator fun invoke(ru: Boolean) = if (ru) this.ru else en
}

/** A plural string: its forms by quantity ("one", "few", "many", "other"), in both languages. */
@Immutable
class Pid(val en: Map<String, String>, val ru: Map<String, String>)

/** A list of strings in both languages. */
@Immutable
class Aid(val en: List<String>, val ru: List<String>)

/** The plural form for [n]: the CLDR rules for English and Russian. */
fun quantity(n: Int, ru: Boolean): String {
    if (!ru) return if (n == 1) "one" else "other"
    val a = kotlin.math.abs(n)
    val m10 = a % 10
    val m100 = a % 100
    return when {
        m10 == 1 && m100 != 11 -> "one"
        m10 in 2..4 && m100 !in 12..14 -> "few"
        else -> "many"
    }
}

/**
 * What a screen needs from around it: the app's books ([container]) and the language. It stands where an Android
 * Context stood, with the same calls for strings, so the screens read as they did.
 */
@Immutable
class Ctx(val container: AppContainer, val ru: Boolean) {
    val locale: Locale = Locale(if (ru) "ru" else "en")

    fun getString(id: Sid): String = id(ru)
    fun getString(id: Sid, vararg args: Any?): String = fmt(id(ru), args)
    fun getQuantityString(id: Pid, n: Int, vararg args: Any?): String {
        val forms = if (ru) id.ru else id.en
        val s = forms[quantity(n, ru)] ?: forms["other"] ?: forms.values.first()
        return fmt(s, args)
    }
    fun getStringArray(id: Aid): List<String> = if (ru) id.ru else id.en

    /** Mirrors Context.resources, so `ctx.resources.getQuantityString(…)` keeps working. */
    val resources: Ctx get() = this
}

val LocalContext = staticCompositionLocalOf<Ctx> { error("no Ctx: wrap the screens in LipovkaApp") }

@Composable
fun stringResource(id: Sid): String = LocalContext.current.getString(id)

@Composable
fun stringResource(id: Sid, vararg args: Any?): String = LocalContext.current.getString(id, *args)

@Composable
fun pluralStringResource(id: Pid, count: Int): String = LocalContext.current.getQuantityString(id, count)

@Composable
fun pluralStringResource(id: Pid, count: Int, vararg args: Any?): String = LocalContext.current.getQuantityString(id, count, *args)
