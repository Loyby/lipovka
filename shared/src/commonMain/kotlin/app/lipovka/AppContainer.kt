package app.lipovka

import app.lipovka.compat.System
import app.lipovka.data.Backup
import app.lipovka.data.Carried
import app.lipovka.data.Content
import app.lipovka.data.Event
import app.lipovka.data.Post
import app.lipovka.data.PostDb
import app.lipovka.data.Mail
import app.lipovka.data.Settings
import app.lipovka.data.SettingsStore
import app.lipovka.domain.Cal
import app.lipovka.domain.Env
import app.lipovka.domain.Game
import app.lipovka.domain.GameState
import app.lipovka.domain.Look
import app.lipovka.domain.Sun
import app.lipovka.domain.Weather
import app.lipovka.time.LocalDate
import app.lipovka.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The app's one set of books, settings and content, shared by every screen (and on Android by the widget and workers). */
class AppContainer(val platform: Platform) {
    val db: PostDb = PostDb.build(platform.database())
    val settings = SettingsStore(platform.settingsPath)
    val content = Content.load(platform)
    val game = Game(content)
    val post = Post(db, game)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Fire-and-forget so a write survives the screen being closed right after tapping. */
    fun write(block: suspend AppContainer.() -> Unit) {
        scope.launch { block() }
    }

    /** The events going on today in the districts already open. */
    fun events(date: LocalDate, s: GameState?): List<Event> =
        content.events.filter { Cal.within(date, it.start, it.end) && (s == null || s.open(it.district)) }

    /** The world outside right now, as the scenes should draw it. */
    fun env(now: LocalDateTime, settings: Settings, s: GameState?): Env {
        val p = settings.place
        val sun = p?.let { Sun.times(now.toLocalDate(), it.lat, it.lon) } ?: Sun.fallback(now.toLocalDate())
        val w = settings.weather
        val fresh = p != null && w != null && System.currentTimeMillis() - w.at < Weather.FRESH_MS
        val south = (p?.lat ?: 1.0) < 0
        val weather = if (fresh) w!! else Weather.simulated(now, Cal.villageMonth(now.toLocalDate(), south))
        val looks = events(now.toLocalDate(), s).map { Look.of(it.look) }.filter { it != Look.NONE }.toSet()
        return Env.build(now, sun, weather, fresh, looks, south)
    }

    /** Fetches the weather if a place is set and the last report is over an hour old. */
    suspend fun refreshWeather(force: Boolean = false) {
        val s = settings.now()
        val p = s.place ?: return
        val w = s.weather
        if (!force && w != null && System.currentTimeMillis() - w.at < 60 * 60 * 1000L) return
        val now = runCatching { withContext(Dispatchers.IO) { Weather.fetch(p.lat, p.lon) } }.getOrNull() ?: return
        settings.update { it.copy(weather = now) }
    }

    /** Reads the steps the phone counted and sorts the morning post. Returns the letters delivered. */
    suspend fun sync(): List<Mail> = platform.sync(this)

    /** The whole post office as a backup file's text. */
    suspend fun backup(): String {
        val set = settings.now()
        return Backup.write(post.snapshot(), Carried(set.place, set.notify))
    }

    /** Replaces the post office with a backup's. Throws if the text isn't a Lipovka backup; nothing changes then. */
    suspend fun restore(text: String) {
        val (books, carried) = Backup.read(text)
        post.restore(books)
        settings.update { it.copy(onboarded = true, place = carried.place, weather = null, notify = carried.notify, milestonesSeen = null, lampOut = -1, calendarTorn = LocalDate.now().toEpochDay()) }
        runCatching { refreshWeather(force = true) }
        runCatching { platform.changed(this) }
    }
}
