package io.github.drusin.kidscreentime.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.drusin.kidscreentime.KidScreenTimeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when the alarm clock of the [AlarmKern] goes off. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kern = (context.applicationContext as KidScreenTimeApp).container.alarmKern
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                kern.trigger()
            } finally {
                result.finish()
            }
        }
    }
}

/**
 * After a reboot (or an app update) all alarms are gone: the process starts, `KidScreenTimeApp` begins to observe the
 * local stand, and this receiver plans once for certain.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kern = (context.applicationContext as KidScreenTimeApp).container.alarmKern
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                kern.replan()
            } finally {
                result.finish()
            }
        }
    }
}
