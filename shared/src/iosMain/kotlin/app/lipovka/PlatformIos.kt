package app.lipovka

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
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.lipovka.ui.Home
import app.lipovka.ui.LocalContext
import app.lipovka.ui.R
import app.lipovka.ui.Sid
import app.lipovka.ui.components.Panel
import app.lipovka.ui.components.PixelButton
import app.lipovka.ui.stringResource
import app.lipovka.ui.theme.C
import app.lipovka.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.CoreMotion.CMPedometer
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private object Ios {
    val stepsOff = Sid(
        "Steps are off. Allow Motion & Fitness for Lipovka in the iPhone's Settings, or the bag won't move.",
        "Шаги выключены. Разрешите Липовке «Движение и фитнес» в Настройках iPhone, иначе сумка не сдвинется.",
    )
    val openSettings = Sid("Open Settings", "Открыть Настройки")
    val source = Sid(
        "Steps come from the iPhone itself: it counts them all the time, even with the app closed, and an Apple Watch adds its own.",
        "Шаги считает сам iPhone: всё время, даже когда приложение закрыто, а Apple Watch добавляет свои.",
    )
}

private fun motionDenied(): Boolean {
    val s = CMPedometer.authorizationStatus()
    return s == CMAuthorizationStatusDenied || s == CMAuthorizationStatusRestricted
}

private fun openSettings() {
    val u = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(u, options = emptyMap<Any?, Any>(), completionHandler = null)
}

@Composable
actual fun rememberAsk(perm: Perm, onResult: (Boolean) -> Unit): () -> Unit {
    val result by rememberUpdatedState(onResult)
    val c = LocalContext.current.container
    return remember(perm) {
        {
            when (perm) {
                Perm.STEPS -> c.scope.launch {
                    runCatching { (c.platform as IosPlatform).askMotion() }
                    withContext(Dispatchers.Main) { result(!motionDenied()) }
                }
                Perm.NOTIFY -> UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                    UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
                ) { ok, _ -> dispatch_async(dispatch_get_main_queue()) { result(ok) } }
            }
            Unit
        }
    }
}

@Composable
actual fun StepsNotice(home: Home) {
    val c = LocalContext.current.container
    var denied by remember { mutableStateOf(motionDenied()) }
    // coming back from Settings: look again
    LifecycleResumeEffect(Unit) {
        denied = motionDenied()
        if (!denied) c.write { sync() }
        onPauseOrDispose { }
    }
    if (!CMPedometer.isStepCountingAvailable()) {
        Panel(fill = C.paper2) { Text(stringResource(R.string.no_sensor), style = T.Sub) }
    } else if (denied) {
        Panel(fill = C.telegram) {
            Text(stringResource(Ios.stepsOff), style = T.Body)
            Spacer(Modifier.height(8.dp))
            PixelButton(stringResource(Ios.openSettings), { openSettings() }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
actual fun StepSource(health: Boolean) {
    var ok by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { ok = CMPedometer.isStepCountingAvailable() && !motionDenied() }
    Text(stringResource(Ios.source), style = T.Body)
    when (ok) {
        true -> Text(stringResource(R.string.steps_ok), style = T.Body)
        false -> {
            Text(stringResource(Ios.stepsOff), style = T.Body)
            PixelButton(stringResource(Ios.openSettings), { openSettings() }, Modifier.fillMaxWidth())
        }
        null -> Unit
    }
}

/** The iPhone has no back button: the screens close with their own buttons. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

/** The view controller on top, to show a picker over. */
internal fun top(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

/** Kept alive while its picker is open: UIKit holds its delegate only weakly. */
private var delegate: PickerDelegate? = null

private class PickerDelegate(val picked: (NSURL?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        delegate = null
        picked(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        delegate = null
        picked(null)
    }
}

@Composable
actual fun rememberFiles(): Files {
    val c = LocalContext.current.container
    return remember(c) {
        object : Files {
            override fun save(name: String, mime: String, produce: suspend () -> String, done: (Boolean?) -> Unit) {
                c.scope.launch(Dispatchers.Main) {
                    // the file is written first, then handed to the Files app to keep where the person chooses
                    val url = runCatching {
                        val text = produce()
                        withContext(Dispatchers.IO) {
                            val u = NSURL.fileURLWithPath(NSTemporaryDirectory() + name)
                            if (!NSString.create(string = text).writeToURL(u, atomically = true, encoding = NSUTF8StringEncoding, error = null)) error("can't write")
                            u
                        }
                    }.getOrNull() ?: return@launch done(false)
                    val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
                    val d = PickerDelegate { done(if (it != null) true else null) }
                    delegate = d
                    picker.delegate = d
                    top()?.presentViewController(picker, animated = true, completion = null) ?: done(false)
                }
            }

            override fun open(done: (String?) -> Unit) {
                val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText, UTTypeData), asCopy = true)
                val d = PickerDelegate { url ->
                    if (url == null) done(null)
                    // unreadable: handed on as text that isn't a backup, so the restore reports it failed
                    else done(NSString.stringWithContentsOfURL(url, encoding = NSUTF8StringEncoding, error = null) ?: "")
                }
                delegate = d
                picker.delegate = d
                top()?.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}
