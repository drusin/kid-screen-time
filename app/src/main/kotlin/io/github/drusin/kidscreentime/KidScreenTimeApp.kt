package io.github.drusin.kidscreentime

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KidScreenTimeApp : Application() {
    private var injected: AppContainer? = null
    private val defaultContainer: AppContainer by lazy { AppContainer(this) }

    val container: AppContainer get() = injected ?: defaultContainer

    /** Lives as long as the process: work that must go on without a screen (the alarm). */
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Tests (Robolectric) swap in their own container, e.g. for services that find it through the Application. */
    fun injectContainerForTest(container: AppContainer) {
        injected = container
    }

    override fun onCreate() {
        super.onCreate()
        // Every process start (app start, boot receiver, alarm, push) plans the alarm from the local stand and
        // again after every change of it (Start, Stopp, every sync). This is the one hook of the Alarmkern.
        container.alarmKern.observe(processScope)
    }
}
