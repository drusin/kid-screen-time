package io.github.drusin.kidscreentime.alarm

import android.content.Context
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Alarmverlauf
import java.time.Instant

/**
 * What the Alarmkern already did for the running Sitzung (Vorwarnung given for which end of the Kontingent, last ring). `domain` has no history, so
 * the device keeps it here, per Sitzung, and it survives a restart.
 */
interface AlarmMemory {
    /** The history of [sessionId]; none when it is another Sitzung than the remembered one. */
    fun load(sessionId: String): Alarmverlauf
    fun save(sessionId: String, verlauf: Alarmverlauf)
    fun clear()
}

class SharedPreferencesAlarmMemory(context: Context) : AlarmMemory {
    private val prefs = context.getSharedPreferences("alarm_memory", Context.MODE_PRIVATE)

    override fun load(sessionId: String): Alarmverlauf {
        if (prefs.getString(KEY_SESSION, null) != sessionId) return Alarmverlauf.KEINER
        return Alarmverlauf(
            vorwarnungFuer = prefs.getLong(KEY_WARNED_FOR, NONE).takeIf { it != NONE }?.let(Instant::ofEpochMilli),
            letzterAlarm = prefs.getLong(KEY_LAST_ALARM, NONE).takeIf { it != NONE }?.let(Instant::ofEpochMilli),
        )
    }

    override fun save(sessionId: String, verlauf: Alarmverlauf) {
        prefs.edit()
            .putString(KEY_SESSION, sessionId)
            .putLong(KEY_WARNED_FOR, verlauf.vorwarnungFuer?.toEpochMilli() ?: NONE)
            .putLong(KEY_LAST_ALARM, verlauf.letzterAlarm?.toEpochMilli() ?: NONE)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_SESSION = "session"
        const val KEY_WARNED_FOR = "warned_for"
        const val KEY_LAST_ALARM = "last_alarm"
        const val NONE = Long.MIN_VALUE
    }
}
