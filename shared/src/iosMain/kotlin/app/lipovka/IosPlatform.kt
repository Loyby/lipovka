package app.lipovka

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.lipovka.compat.toByteArray
import app.lipovka.data.Mail
import app.lipovka.data.PostDb
import app.lipovka.time.LocalDate
import app.lipovka.time.ZoneId
import app.lipovka.ui.R
import app.lipovka.ui.Sid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreMotion.CMPedometer
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.preferredLanguages
import platform.UIKit.UIApplication
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * The iPhone's side of the app. Steps come from Core Motion: the iPhone counts them all the time on its own
 * (and an Apple Watch adds its own through it) and keeps a week of them, so nothing has to run in the background:
 * each time the app opens it asks for the totals of the days since it last looked.
 */
class IosPlatform : Platform {
    override val debug: Boolean = false
    override val demo: Boolean = false

    private val defaults = NSUserDefaults.standardUserDefaults
    private var lang by mutableStateOf(defaults.stringForKey(LANG) ?: "")

    override val ru: Boolean
        get() = when (lang) {
            "ru" -> true
            "en" -> false
            else -> (NSLocale.preferredLanguages.firstOrNull() as? String)?.startsWith("ru") == true
        }

    override fun langTag(): String = lang

    override fun setLang(tag: String) {
        lang = tag
        defaults.setObject(tag, forKey = LANG)
    }

    override fun asset(path: String): ByteArray? = assetPath(path)?.let { NSData.dataWithContentsOfFile(it)?.toByteArray() }

    override fun assetList(dir: String): List<String> {
        val base = NSBundle.mainBundle.resourcePath ?: return emptyList()
        return NSFileManager.defaultManager.contentsOfDirectoryAtPath("$base/assets/$dir", error = null)?.map { it.toString() }.orEmpty()
    }

    override fun openUrl(url: String) {
        val u = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(u, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    override val settingsPath: String get() = "${documents()}/settings.preferences_pb"

    override fun database(): RoomDatabase.Builder<PostDb> =
        Room.databaseBuilder<PostDb>(name = "${documents()}/lipovka.db")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)

    // ------------------------------------------------------------------ steps

    private val pedometer = CMPedometer()

    /** Steps the iPhone counted between two moments; null if it can't tell (no permission, no motion chip). */
    private suspend fun steps(fromMs: Long, toMs: Long): Long? = suspendCancellableCoroutine { cont ->
        pedometer.queryPedometerDataFromDate(
            NSDate.dateWithTimeIntervalSince1970(fromMs / 1000.0),
            toDate = NSDate.dateWithTimeIntervalSince1970(toMs / 1000.0),
        ) { data, error ->
            cont.resume(if (error != null) null else data?.numberOfSteps?.longValue ?: 0L)
        }
    }

    /** Shows the iPhone's "Motion & Fitness" question, once, by asking for a little of the steps. */
    suspend fun askMotion() {
        if (!CMPedometer.isStepCountingAvailable()) return
        val now = app.lipovka.compat.System.currentTimeMillis()
        steps(now - 60_000, now)
    }

    override suspend fun sync(c: AppContainer): List<Mail> {
        if (!CMPedometer.isStepCountingAvailable()) { c.post.tick(); return emptyList() }
        if (c.post.state.value == null) c.post.refresh()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now().toEpochDay()
        // The days since the job began, a week back at most (all the iPhone keeps); the post adds only what it
        // hasn't carried yet, so asking again for the same days is harmless.
        val start = c.post.state.value?.start ?: today
        val from = maxOf(start, today - DAYS_BACK)
        val now = app.lipovka.compat.System.currentTimeMillis()
        val totals = mutableMapOf<Long, Long>()
        for (d in from..today) {
            val a = LocalDate.ofEpochDay(d).atStartOfDay(zone).toInstant().toEpochMilli()
            val b = minOf(now, LocalDate.ofEpochDay(d + 1).atStartOfDay(zone).toInstant().toEpochMilli())
            totals[d] = steps(a, b) ?: run { c.post.tick(); return emptyList() }
        }
        return c.post.health(totals)
    }

    override suspend fun foreground(c: AppContainer, health: Boolean) {
        // The iPhone counts on its own; while the app is open it's asked every few seconds, so the bag moves as you walk.
        while (true) {
            runCatching { c.sync() }
            delay(LIVE_MS)
        }
    }

    override fun background(c: AppContainer, health: Boolean) {
        c.scope.launch { runCatching { scheduleMorning(c) } }
    }

    /** One gentle note for the next morning at nine, if notifications are on: the post is sorted by then. */
    private suspend fun scheduleMorning(c: AppContainer) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(MORNING))
        val set = c.settings.now()
        if (!set.notify || !set.onboarded) return
        val content = UNMutableNotificationContent()
        content.setTitle(R.string.n_morning_title(ru))
        content.setBody(MORNING_TEXT(ru))
        val at = NSDateComponents()
        at.setHour(9)
        at.setMinute(0)
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(at, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(MORNING, content, trigger), withCompletionHandler = null)
    }

    companion object {
        private const val LANG = "lipovka.lang"
        private const val MORNING = "morning"
        private const val DAYS_BACK = 6L
        private const val LIVE_MS = 5_000L
        private val MORNING_TEXT = Sid("The morning post is sorted. Time for the round!", "Утренняя почта разобрана. Пора на обход!")

        fun documents(): String =
            (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String) ?: ""

        /** A file in the app's assets folder, as a path; null if it isn't there. */
        fun assetPath(path: String): String? {
            val base = NSBundle.mainBundle.resourcePath ?: return null
            val p = "$base/assets/$path"
            return p.takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }
        }

        /** A file in the app bundle outside the assets (the iPhone's recordings), as a path; null if it isn't there. */
        fun bundlePath(path: String): String? {
            val base = NSBundle.mainBundle.resourcePath ?: return null
            return "$base/$path".takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }
        }

        /** A file at the top of the app bundle (the font). */
        fun bundleFile(name: String): ByteArray? {
            val base = NSBundle.mainBundle.resourcePath ?: return null
            return NSData.dataWithContentsOfFile("$base/$name")?.toByteArray()
        }
    }
}
