package io.github.drusin.kidscreentime.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.drusin.kidscreentime.KidScreenTimeApp
import io.github.drusin.kidscreentime.ui.theme.KidScreenTimeTheme

/** The full-screen Alarm. Shows over the lock screen and other apps (full-screen intent of [AlarmService]). */
class AlarmActivity : ComponentActivity() {
    private val viewModel: AlarmViewModel by viewModels {
        val container = (application as KidScreenTimeApp).container
        viewModelFactory {
            initializer {
                AlarmViewModel(container.alarmKern, container.childRepository, container.testSession) { container.alarmEffects.silence() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            KidScreenTimeTheme {
                AlarmScreen(viewModel, onFinished = ::finish)
            }
        }
    }
}
