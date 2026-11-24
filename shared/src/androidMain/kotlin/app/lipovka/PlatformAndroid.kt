package app.lipovka

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lipovka.ui.Home
import app.lipovka.ui.LocalContext
import app.lipovka.ui.R
import app.lipovka.ui.Sid
import app.lipovka.ui.components.Kind
import app.lipovka.ui.components.Panel
import app.lipovka.ui.components.PixelButton
import app.lipovka.ui.stringResource
import app.lipovka.ui.theme.C
import app.lipovka.ui.theme.T
import app.lipovka.work.HealthSteps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberAsk(perm: Perm, onResult: (Boolean) -> Unit): () -> Unit {
    val result by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result(it) }
    return {
        when (perm) {
            Perm.STEPS -> if (Build.VERSION.SDK_INT >= 29) launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) else result(true)
            Perm.NOTIFY -> if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else result(true)
        }
    }
}

@Composable
actual fun StepsNotice(home: Home) {
    val c = LocalContext.current.container
    // With Health Connect the phone's own counter isn't used; its state shows in settings.
    if (home.settings.healthFrom >= 0) return
    var allowed by remember { mutableStateOf(c.sensor.allowed) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> allowed = ok; if (ok) c.write { sync() } }
    if (!c.sensor.present) {
        Panel(fill = C.paper2) { Text(stringResource(R.string.no_sensor), style = T.Sub) }
    } else if (!allowed && Build.VERSION.SDK_INT >= 29) {
        Panel(fill = C.telegram) {
            Text(stringResource(R.string.steps_permission), style = T.Body)
            Spacer(Modifier.height(8.dp))
            PixelButton(stringResource(R.string.allow), { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }, Modifier.fillMaxWidth())
        }
    }
}

/** Where the steps come from: the phone's own counter, or Health Connect. */
@Composable
actual fun StepSource(health: Boolean) {
    val c = LocalContext.current.container
    val context = c.android.context
    // null while asking Health Connect whether reading is still allowed.
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(health) { allowed = c.health.allowed() }
    // Why tapping Health Connect didn't switch to it.
    var note by remember { mutableStateOf<Sid?>(null) }
    fun useHealth() = c.write {
        settings.update { it.copy(healthFrom = java.time.LocalDate.now().toEpochDay()) }
        sync()
    }
    val ask = rememberLauncherForActivityResult(HealthSteps.requestContract()) { got ->
        allowed = HealthSteps.READ in got
        if (allowed == true) { note = null; useHealth() } else note = R.string.health_denied
    }
    fun pickHealth() {
        when {
            c.health.available -> ask.launch(c.health.permissions())
            c.health.installable -> { note = R.string.health_install; runCatching { context.startActivity(c.health.storeIntent().addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            else -> note = R.string.health_none
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelButton(stringResource(R.string.source_phone), {
            note = null
            c.write { settings.update { it.copy(healthFrom = -1) }; sync() }
        }, Modifier.weight(1f), kind = if (!health) Kind.PRIMARY else Kind.GHOST)
        PixelButton(stringResource(R.string.source_health), { pickHealth() }, Modifier.weight(1f), kind = if (health) Kind.PRIMARY else Kind.GHOST)
    }
    val status = when {
        !health -> note ?: if (c.sensor.allowed && c.sensor.present) R.string.steps_ok else R.string.steps_off
        allowed == null -> null
        allowed == true -> R.string.health_ok
        c.health.available -> R.string.health_denied
        c.health.installable -> R.string.health_install
        else -> R.string.health_none
    }
    status?.let { Text(stringResource(it), style = T.Body) }
    if (health && allowed == false) {
        if (c.health.available) PixelButton(stringResource(R.string.health_allow), { pickHealth() }, Modifier.fillMaxWidth())
        else if (c.health.installable) PixelButton(stringResource(R.string.health_get), { pickHealth() }, Modifier.fillMaxWidth())
    }
    Text(stringResource(R.string.source_hint), style = T.Small)
    Text(stringResource(R.string.background_hint), style = T.Small)
    PixelButton(stringResource(R.string.background_open), {
        val context = c.android.context
        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }, Modifier.fillMaxWidth(), kind = Kind.GHOST)
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun rememberFiles(): Files {
    val c = LocalContext.current.container
    val resolver = c.android.context.contentResolver
    // what the next pick is for: the text to write and who to tell, or who wants the text read
    var saving by remember { mutableStateOf<Pair<suspend () -> String, (Boolean?) -> Unit>?>(null) }
    var opening by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val (produce, done) = saving ?: return@rememberLauncherForActivityResult
        saving = null
        if (uri == null) { done(null); return@rememberLauncherForActivityResult }
        c.scope.launch(Dispatchers.Main) {
            val r = runCatching {
                val text = produce()
                withContext(Dispatchers.IO) {
                    (resolver.openOutputStream(uri, "wt") ?: error("can't write $uri")).use { it.write(text.toByteArray()) }
                }
            }
            done(r.isSuccess)
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val done = opening ?: return@rememberLauncherForActivityResult
        opening = null
        if (uri == null) { done(null); return@rememberLauncherForActivityResult }
        c.scope.launch(Dispatchers.Main) {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    (resolver.openInputStream(uri) ?: error("can't read $uri")).use { it.readBytes().toString(Charsets.UTF_8) }
                }
            }.getOrNull()
            // unreadable: handed on as text that isn't a backup, so the restore reports it failed
            done(text ?: "")
        }
    }
    return remember(create, open) {
        object : Files {
            override fun save(name: String, mime: String, produce: suspend () -> String, done: (Boolean?) -> Unit) {
                saving = produce to done
                create.launch(name)
            }

            override fun open(done: (String?) -> Unit) {
                opening = done
                // Backups are JSON, but file managers and chats often hand them over as plain or unknown files.
                open.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }
    }
}
