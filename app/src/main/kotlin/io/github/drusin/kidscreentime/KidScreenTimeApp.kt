package io.github.drusin.kidscreentime

import android.app.Application

class KidScreenTimeApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
