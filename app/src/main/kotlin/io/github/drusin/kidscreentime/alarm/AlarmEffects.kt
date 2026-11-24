package io.github.drusin.kidscreentime.alarm

import android.content.Context

/** What the Alarmkern lets happen at the device when a time is due. An interface so tests record instead of playing sound. */
interface AlarmEffects {
    /** Vorwarnung: a short sound. The snackbar is the screen's business. */
    fun warn()

    /** Alarm: ring (foreground service plus full-screen intent). */
    fun ring()

    /** Ends a ringing Alarm (the Restzeit is above 0 again, or the Sitzung ended). A Vorwarnung is not touched. */
    fun silence()

    companion object {
        /** No effect at all (tests that do not care about the alarm). */
        val NONE = object : AlarmEffects {
            override fun warn() = Unit
            override fun ring() = Unit
            override fun silence() = Unit
        }
    }
}

class AndroidAlarmEffects(private val context: Context) : AlarmEffects {
    override fun warn() = AlarmService.start(context, AlarmService.ACTION_WARN)

    override fun ring() = AlarmService.start(context, AlarmService.ACTION_RING)

    override fun silence() = AlarmService.stopRinging(context)
}
