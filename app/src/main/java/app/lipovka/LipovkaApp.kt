package app.lipovka

import android.app.Application
import android.content.Context
import app.lipovka.data.DemoSeed
import app.lipovka.work.Alarms
import app.lipovka.work.PostWidget
import app.lipovka.work.WalkService
import kotlinx.coroutines.launch

class LipovkaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val platform = AndroidPlatform(
            this,
            debug = BuildConfig.DEBUG || BuildConfig.DEMO,
            demo = BuildConfig.DEMO,
            shots = BuildConfig.SHOTS,
            walk = object : AndroidPlatform.Walk {
                override fun start(context: Context) = WalkService.start(context)
                override fun stop(context: Context) = WalkService.stop(context)
            },
            widgets = { PostWidget.updateAll(it) },
        )
        container = AppContainer(platform)
        if (BuildConfig.DEMO) container.scope.launch { DemoSeed.seedIfEmpty(container) }
        runCatching { Alarms.schedule(this) }
    }
}

val Context.container: AppContainer get() = (applicationContext as LipovkaApp).container
