package app.lipovka

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.room.Room
import androidx.room.RoomDatabase
import app.lipovka.data.Mail
import app.lipovka.data.PostDb
import app.lipovka.work.HealthSteps
import app.lipovka.work.StepSensor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate

/**
 * Android's side of the app: steps from the phone's step counter (or Health Connect, if chosen), the in-app
 * language through AppCompat, files through the system pickers. The app module hands in what only it can do:
 * the widget, and the foreground service that keeps counting while the app is out of sight.
 */
class AndroidPlatform(
    context: Context,
    override val debug: Boolean,
    override val demo: Boolean,
    override val shots: Boolean = false,
    private val walk: Walk = Walk.NONE,
    private val widgets: suspend (Context) -> Unit = {},
) : Platform {
    val context: Context = context.applicationContext
    val sensor = StepSensor(this.context)
    val health = HealthSteps(this.context)

    /** The quiet foreground service that counts while the app is out of sight. */
    interface Walk {
        fun start(context: Context)
        fun stop(context: Context)

        companion object {
            val NONE = object : Walk {
                override fun start(context: Context) = Unit
                override fun stop(context: Context) = Unit
            }
        }
    }

    init {
        app = this.context
    }

    override val ru: Boolean get() = context.localized().resources.configuration.locales[0].language == "ru"

    override fun langTag(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').substringBefore(',')

    override fun setLang(tag: String) {
        AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
    }

    override fun asset(path: String): ByteArray? = runCatching { context.assets.open(path).use { it.readBytes() } }.getOrNull()

    override fun assetList(dir: String): List<String> = context.assets.list(dir).orEmpty().toList()

    override fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The file `preferencesDataStore("settings")` always used, so the settings carry over. */
    override val settingsPath: String get() = context.filesDir.resolve("datastore/settings.preferences_pb").absolutePath

    override fun database(): RoomDatabase.Builder<PostDb> = Room.databaseBuilder<PostDb>(context, context.getDatabasePath("lipovka.db").absolutePath)

    override suspend fun sync(c: AppContainer): List<Mail> {
        val from = c.settings.now().healthFrom
        if (from >= 0) {
            // A week back at most: watches and bands may hand over a day's steps only when they next sync.
            val today = java.time.LocalDate.now()
            val start = maxOf(java.time.LocalDate.ofEpochDay(from), today.minusDays(HEALTH_DAYS))
            val totals = health.days(start, today)
            return if (totals != null) c.post.health(totals) else { c.post.tick(); emptyList() }
        }
        val v = sensor.read()
        return if (v != null) c.post.reading(v) else { c.post.tick(); emptyList() }
    }

    override suspend fun foreground(c: AppContainer, health: Boolean) {
        walk.stop(context)
        if (health) {
            // Health Connect keeps the steps itself; asking now and then is enough.
            while (true) { c.sync(); delay(60_000) }
        } else if (sensor.allowed) {
            // The counter's own first value comes as soon as it's subscribed, so no separate read to wait on.
            sensor.live().conflate().collect { v -> c.post.reading(v); delay(1000) }
        }
    }

    // Not needed with Health Connect: the health app counts on its own.
    override fun background(c: AppContainer, health: Boolean) {
        if (!health) walk.start(context)
    }

    override suspend fun changed(c: AppContainer) = widgets(context)

    companion object {
        private const val HEALTH_DAYS = 6L

        /** The app's context, for the few things (the vibrator) reached without one at hand. */
        @SuppressLint("StaticFieldLeak")
        @Volatile var app: Context? = null
            private set
    }
}

val AppContainer.android: AndroidPlatform get() = platform as AndroidPlatform
val AppContainer.sensor: StepSensor get() = android.sensor
val AppContainer.health: HealthSteps get() = android.health

/** Resources in the in-app language, which AppCompat doesn't localise outside activities before Android 13. */
fun Context.localized(): Context {
    val tags = AppCompatDelegate.getApplicationLocales()
    if (tags.isEmpty) return this
    val config = android.content.res.Configuration(resources.configuration)
    config.setLocale(tags[0])
    return createConfigurationContext(config)
}

/** Is this context in Russian? */
val Context.ru: Boolean get() = resources.configuration.locales[0].language == "ru"
