package app.lipovka

import androidx.compose.runtime.Composable
import androidx.room.RoomDatabase
import app.lipovka.data.Mail
import app.lipovka.data.PostDb
import app.lipovka.ui.Home

/**
 * What each phone does its own way: where the steps come from, files, links, the language, the database.
 * Android's is in androidMain (the step counter or Health Connect), the iPhone's in iosMain (Core Motion).
 */
interface Platform {
    /** A debug or demo build: shows the "+1000 steps" button. */
    val debug: Boolean

    /** The demo build (a few months already walked). */
    val demo: Boolean

    /** Screenshots want still pictures. */
    val shots: Boolean get() = false

    /** Is the app speaking Russian now? Read in composition, so a change of language redraws. */
    val ru: Boolean

    /** The language chosen in the app: "" for the phone's own, "ru" or "en". */
    fun langTag(): String

    fun setLang(tag: String)

    /** A file shipped with the app (the content, the sounds), by its path under assets/. */
    fun asset(path: String): ByteArray?

    /** The files in an assets folder. */
    fun assetList(dir: String): List<String>

    fun openUrl(url: String)

    /** Where the settings live. */
    val settingsPath: String

    fun database(): RoomDatabase.Builder<PostDb>

    /** Reads the steps the phone has counted and carries them round. Returns the letters delivered. */
    suspend fun sync(c: AppContainer): List<Mail>

    /** The app is in sight: counts the steps live while it's open, until cancelled when it goes. */
    suspend fun foreground(c: AppContainer, health: Boolean)

    /** The app has gone out of sight. */
    fun background(c: AppContainer, health: Boolean)

    /** Something shown outside the app changed (the widget). */
    suspend fun changed(c: AppContainer) = Unit
}

/** What a permission is asked for. */
enum class Perm { STEPS, NOTIFY }

/** Asks for [perm] when called; [onResult] hears whether it was given (true where nothing needs asking). */
@Composable
expect fun rememberAsk(perm: Perm, onResult: (Boolean) -> Unit): () -> Unit

/** A note on the post screen when the steps can't be counted (no permission, no sensor). */
@Composable
expect fun StepsNotice(home: Home)

/** The settings' section on where the steps come from. */
@Composable
expect fun StepSource(health: Boolean)

/** The system back gesture closes what's open. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Saving a file somewhere the person picks, and opening one they pick. */
interface Files {
    /** Asks where to save [name], then writes [produce]'s text there; [done] hears true, false on failure, null if cancelled. */
    fun save(name: String, mime: String, produce: suspend () -> String, done: (Boolean?) -> Unit)

    /** Asks for a file and reads it; [done] hears its text, or null if cancelled or unreadable. */
    fun open(done: (String?) -> Unit)
}

@Composable
expect fun rememberFiles(): Files
