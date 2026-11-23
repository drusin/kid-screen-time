# Wie klingelt ein Alarm auf Samsung-Android zuverlässig im Hintergrund und Standby?

Type: research
Status: open
Blocked by: -

## Question

Welche Android-Mechanismen garantieren, dass am Kindgerät (aktuelles Samsung-Tablet, One UI / Android 14+) zu einem vorab berechneten Zeitpunkt ein **hörbarer** Alarm klingelt? Das soll auch gelten, wenn eine andere App im Vordergrund ist, das Tablet im Standby/Doze ist oder die App seit Stunden nicht angefasst wurde. Der Alarm wiederholt sich alle 5 Minuten, solange die Sitzung läuft, und davor gibt es eine Vorwarnung.

Zu klären:
- `AlarmManager.setAlarmClock` vs. `setExactAndAllowWhileIdle`. Berechtigungen `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` und was davon bei Sideloading gilt.
- Ton abspielen: Alarm-Audiostream, Verhalten bei Stumm/Nicht-stören, Full-Screen-Intent vs. Notification, ob ein Foreground Service nötig ist (welcher Typ?).
- Samsung-spezifisch: „Schlafende Apps“, Akku-Optimierung, „Nie schlafende Apps“. Was muss einmalig manuell eingestellt werden (Quelle: dontkillmyapp.com und Samsung-Doku)?
- Kann eine **PWA/Webapp** das überhaupt (Web Push + Notification-Ton im Doze)? Kurze, belegte Abgrenzung.
- Kann ein Hybrid-Ansatz (Capacitor mit nativem Alarm-Plugin) das genauso zuverlässig wie natives Kotlin?
