package io.github.drusin.kidscreentime

import android.app.Application

class KidScreenTimeApp : Application() {
    private var injected: AppContainer? = null
    private val defaultContainer: AppContainer by lazy { AppContainer(this) }

    val container: AppContainer get() = injected ?: defaultContainer

    /** Tests (Robolectric) swap in their own container, e.g. for services that find it through the Application. */
    fun injectContainerForTest(container: AppContainer) {
        injected = container
    }
}
