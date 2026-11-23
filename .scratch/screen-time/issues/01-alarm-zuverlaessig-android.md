# Wie klingelt ein Alarm auf Samsung-Android zuverlässig im Hintergrund und Standby?

Type: research
Status: resolved
Blocked by: -

## Question

Welche Android-Mechanismen garantieren, dass am Kindgerät (aktuelles Samsung-Tablet, One UI / Android 14+) zu einem vorab berechneten Zeitpunkt ein **hörbarer** Alarm klingelt? Das soll auch gelten, wenn eine andere App im Vordergrund ist, das Tablet im Standby/Doze ist oder die App seit Stunden nicht angefasst wurde. Der Alarm wiederholt sich alle 5 Minuten, solange die Sitzung läuft, und davor gibt es eine Vorwarnung.

Zu klären:
- `AlarmManager.setAlarmClock` vs. `setExactAndAllowWhileIdle`. Berechtigungen `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` und was davon bei Sideloading gilt.
- Ton abspielen: Alarm-Audiostream, Verhalten bei Stumm/Nicht-stören, Full-Screen-Intent vs. Notification, ob ein Foreground Service nötig ist (welcher Typ?).
- Samsung-spezifisch: „Schlafende Apps“, Akku-Optimierung, „Nie schlafende Apps“. Was muss einmalig manuell eingestellt werden (Quelle: dontkillmyapp.com und Samsung-Doku)?
- Kann eine **PWA/Webapp** das überhaupt (Web Push + Notification-Ton im Doze)? Kurze, belegte Abgrenzung.
- Kann ein Hybrid-Ansatz (Capacitor mit nativem Alarm-Plugin) das genauso zuverlässig wie natives Kotlin?

## Answer

Findings: branch `research/alarm-android`, file `research/01-alarm-zuverlaessig-android.md` (local only; push via SSH failed).

**Only native Android works reliably, a PWA does not.** The alarm core must be Kotlin regardless of the UI choice:
- `AlarmManager.setAlarmClock()`: never delayed, leaves Doze. `setExactAndAllowWhileIdle` is limited to about once per 9 minutes in Doze and is too coarse for the 5-minute repeat.
- `USE_EXACT_ALARM` permission. Its Play restriction doesn't matter for sideloading.
- Alarm stream, a short `systemExempted` foreground service while it rings, a full-screen intent (`USE_FULL_SCREEN_INTENT`, check it at setup), and a boot receiver that reschedules the alarm.
- Samsung, one-time setup: "Nie schlafende Apps", battery "Nicht eingeschränkt", allow Alarms in Do Not Disturb, alarm volume above 0. Build an in-app setup checklist for this.
- Capacitor/Vue is possible for the UI if the alarm lives in a separate Kotlin plugin and the session state is stored natively. `@capacitor/local-notifications` alone is not enough.
- Must be tested on the tablet: full-screen permission when sideloaded, and Do Not Disturb defaults. Caveat: some sources were only read through search copies (firewall).
