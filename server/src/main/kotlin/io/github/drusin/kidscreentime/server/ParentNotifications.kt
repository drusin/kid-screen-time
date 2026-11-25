package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.PushSignal
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The texts of the Eltern-Benachrichtigungen (ticket 14). The Kindname always stands first. Sent to all Elterngeräte. */
object ParentNotifications {
    const val TITLE = "Bildschirmzeit"

    fun restzeitNull(event: RestzeitNull, zone: ZoneId): PushSignal {
        val since = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(event.since)
        return PushSignal(TITLE, "${event.childName}: Kontingent abgelaufen (seit $since)")
    }

    fun automaticStop(stop: AutomaticStop): PushSignal =
        PushSignal(TITLE, "${stop.childName}: Automatischer Stopp nach ${stop.overrunLimit.toMinutes()} min Überziehung")
}
