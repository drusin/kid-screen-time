package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.PushSignal
import io.github.drusin.kidscreentime.api.SetupItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The texts of the Eltern-Benachrichtigungen (ticket 14). The Kindname always stands first. Sent to all Elterngeräte. */
object ParentNotifications {
    const val TITLE = "Bildschirmzeit"

    fun restzeitNull(event: RestzeitNull, zone: ZoneId): PushSignal {
        val since = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(event.since)
        return PushSignal(TITLE, "${event.childName}: Kontingent abgelaufen (seit $since)")
    }

    /** The Einrichtungs-Checkliste lost its "vollständig": every missing item in a few words ("Mia: Alarmlautstärke ist 0"). */
    fun setupMissing(childName: String, missing: List<SetupItem>): PushSignal =
        PushSignal(TITLE, "$childName: " + missing.joinToString("; ") { it.problem() })

    private fun SetupItem.problem() = when (this) {
        SetupItem.EXACT_ALARMS -> "Exakte Alarme sind nicht erlaubt"
        SetupItem.FULL_SCREEN_INTENT -> "Vollbild-Benachrichtigung ist nicht erlaubt"
        SetupItem.NOTIFICATIONS -> "Benachrichtigungen sind aus"
        SetupItem.BATTERY -> "Akku ist eingeschränkt"
        SetupItem.ALARM_VOLUME -> "Alarmlautstärke ist 0"
        SetupItem.PUSH_DISTRIBUTOR -> "Kein Push-Verteiler verbunden"
    }

    fun automaticStop(stop: AutomaticStop): PushSignal =
        PushSignal(TITLE, "${stop.childName}: Automatischer Stopp nach ${stop.overrunLimit.toMinutes()} min Überziehung")
}
