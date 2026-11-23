# Research: Zuverlässiger Alarm auf Samsung-Android (Ticket 01)

Ticket: [.scratch/screen-time/issues/01-alarm-zuverlaessig-android.md](../.scratch/screen-time/issues/01-alarm-zuverlaessig-android.md)
Stand: 2026-11-23

> **Hinweis zur Methode:** Die Sandbox-Firewall hat direkte Seitenabrufe (developer.android.com, dontkillmyapp.com) blockiert. Die Aussagen stützen sich auf den suchindexierten Inhalt genau dieser Primärseiten (Android Developers, AOSP, Samsung Developers/Support, Capacitor-Doku, Chrome-Doku). Wo nur Sekundärquellen vorlagen, steht **[unbestätigt]**. Vor der Umsetzung lohnt sich ein kurzer Blick auf die verlinkten Originalseiten.

## Kurzantwort

Nur eine **native Android-App** (Kotlin, oder Capacitor mit eigenem Kotlin-Plugin) schafft das. Der Kern ist **`AlarmManager.setAlarmClock()` + `USE_EXACT_ALARM`**. Beim Auslösen startet ein kurzer **Foreground Service**, der den Ton auf dem **Alarm-Audiostream** (`USAGE_ALARM`) abspielt und eine Benachrichtigung mit **Full-Screen-Intent** zeigt. Dazu kommt einmalig auf dem Tablet: App in **„Nie schlafende Apps“** aufnehmen, Akku auf **„Nicht eingeschränkt“** stellen und prüfen, dass **Alarme in „Nicht stören“ erlaubt** sind. Eine PWA kann das nicht.

## 1. `setAlarmClock` vs. `setExactAndAllowWhileIdle`

| | `setAlarmClock()` | `setExactAndAllowWhileIdle()` |
|---|---|---|
| Doze | Das System verschiebt diese Alarme nie und **verlässt bei Bedarf den Energiesparmodus**, um sie zuzustellen ([Schedule alarms][alarms]) | Feuert in Doze, ist aber **gedrosselt: höchstens ca. 1× pro 9 Minuten pro App** ([Capacitor Local Notifications][cap-ln], [gdelataillade/alarm #439][alarm439]) |
| Sichtbarkeit | Das System darf den Alarm dem Nutzer anzeigen (Wecker-Symbol in der Statusleiste) ([AlarmManagerCompat][amc]) | unsichtbar |
| Berechtigung | Braucht ab Android 14 `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, sonst `SecurityException` ([Android 14: exact alarms denied by default][a14-exact]) | ebenso ([AlarmManager][am]) |

**Konsequenz:** Der 5-Minuten-Wiederholungsalarm **verträgt sich nicht** mit der 9-Minuten-Drossel von `setExactAndAllowWhileIdle`. Also für Vorwarnung, Alarm und jede Wiederholung `setAlarmClock()` nutzen. Bei jedem Auslösen wird der nächste Termin (+5 min) neu geplant, es gibt kein `setRepeating`.

Nach einem Neustart sind alle Alarme gelöscht. Deshalb braucht die App einen `BOOT_COMPLETED`-Receiver, der den nächsten Alarm aus dem gespeicherten Sitzungszustand neu plant ([Schedule alarms][alarms]).

## 2. `SCHEDULE_EXACT_ALARM` vs. `USE_EXACT_ALARM`, auch beim Sideloading

- **`SCHEDULE_EXACT_ALARM`**: Für Neuinstallationen ab Android 14 standardmäßig **verweigert**. Der Nutzer muss es erteilen, und Nutzer oder System können es wieder entziehen ([Android 14 change][a14-exact]). Wird es entzogen, löscht das System die geplanten Alarme, auch die aus `setAlarmClock` ([AlarmManager][am]).
- **`USE_EXACT_ALARM`** (API 33+): wird **bei der Installation automatisch erteilt** und **kann vom Nutzer nicht entzogen werden** ([Schedule alarms][alarms]).
- Die Einschränkung von `USE_EXACT_ALARM` auf Wecker-, Uhr- und Kalender-Apps ist eine **Google-Play-Richtlinie**: „Apps will not be able to publish a version … with this permission unless they qualify“ ([Schedule alarms][alarms], [Esper][esper]). Bei **Sideloading greift diese Prüfung nicht**. Inhaltlich ist die App ohnehin eine Wecker-Funktion.
- Empfehlung: Beide im Manifest deklarieren (`SCHEDULE_EXACT_ALARM` mit `maxSdkVersion="32"`). Zur Sicherheit beim Start `canScheduleExactAlarms()` prüfen und sonst zu `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` führen ([AlarmManager][am]).

## 3. Ton, Nicht-stören, Full-Screen-Intent, Foreground Service

**Audiostream.** Den Ton selbst mit `MediaPlayer`/`Ringtone` und `AudioAttributes.USAGE_ALARM` abspielen. Dann hängt er von der **Alarm-Lautstärke** ab und nicht vom Benachrichtigungston. Ein Notification-Channel-Ton mit `USAGE_ALARM` umgeht DND **nicht** von selbst. Ein `bypassDnd` am Channel braucht zusätzlich Notification-Policy-Zugriff ([Flet/Android-Channel-Doku][flet], [nagios-android #109][nagios]) **[Sekundärquelle]**.

**Nicht stören / Stumm.** In Android-DND sind „Alarme“ eine eigene Ausnahme-Kategorie. Bei Samsung steht sie unter *Benachrichtigungen › Nicht stören › Alarme und Töne* ([Samsung Support DND][sam-dnd]). Laut Samsung-Community-Moderator und Motorola-Support ist sie standardmäßig an **[unbestätigt als offizielle Samsung-Aussage]**. Wenn ein DND-Modus Alarme ausschließt, wird `STREAM_ALARM` stummgeschaltet ([nagios-android #109][nagios]). → **Einmalig prüfen**, dass Alarme in DND erlaubt sind und die Alarm-Lautstärke nicht auf 0 steht.

**Android 17** (Hintergrund-Audio-Härtung): Audio, Audio-Focus und Lautstärkeänderungen aus dem Hintergrund werden eingeschränkt. Für Apps mit targetSdk 37 braucht der FGS „While-in-use“-Fähigkeiten, **außer die App hat die Exact-Alarm-Berechtigung und nutzt `USAGE_ALARM`-Streams**. Alarm-Audio ist damit ausdrücklich ausgenommen ([Background audio hardening][a17-audio], [Android 17 release notes][a17-rn]). Unser Design erfüllt genau diese Ausnahme.

**Full-Screen-Intent vs. Notification.**
- Ab Android 14 darf `USE_FULL_SCREEN_INTENT` standardmäßig nur an Anruf- und Wecker-Apps gehen. Play entzieht die Berechtigung anderen Apps bei der Installation. Prüfen lässt sie sich mit `NotificationManager.canUseFullScreenIntent()`, freischalten mit `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` (Einstellungen › Spezieller App-Zugriff) ([AOSP FSI limits][fsi]).
- Bei Sideloading entzieht Play nichts. Ob One UI die Berechtigung bei Sideload automatisch erteilt, ist **[unbestätigt]** → beim Setup prüfen und notfalls manuell erlauben.
- Der Full-Screen-Intent ist nur UI (Alarmbildschirm über dem Sperrbildschirm). Den **Ton** spielt die App selbst. Bei entsperrtem Gerät und anderer App im Vordergrund erscheint statt des Vollbilds eine Heads-up-Notification, der Ton läuft trotzdem.

**Foreground Service: ja, kurz.** Der `BroadcastReceiver` hat nur ein paar Sekunden Zeit. Für einen hörbaren Alarm über mehrere Sekunden bzw. bis zum Antippen einen FGS starten.
- Ein Start aus dem Hintergrund ist erlaubt, weil **exakte Alarme von den FGS-Startbeschränkungen ausgenommen sind** ([Schedule alarms][alarms], [FGS background-start restrictions][fgs-bg]).
- Ab Android 14 ist ein **FGS-Typ Pflicht** ([FGS types required][fgs-req]). Geeignet ist **`systemExempted`**: Er ist ausdrücklich für Apps mit `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` vorgesehen, die einen FGS für Alarme nutzen ([FGS types][fgs-types]). Alternative: `mediaPlayback`.
- Achtung: Ab Android 15 darf ein `BOOT_COMPLETED`-Receiver **keinen** `mediaPlayback`-FGS starten ([Android 15 FGS changes][a15-fgs]). Der Boot-Receiver plant deshalb nur neu und startet keinen Dienst.
- Kein dauerhaft laufender FGS für die ganze Sitzung nötig. Samsung garantiert seit One UI 6 nur, dass richtlinienkonforme FGS von Android-14-Apps funktionieren ([Samsung App Management][sam-dev]), und ein Dauer-FGS bringt gegenüber `setAlarmClock` keinen Zuverlässigkeitsgewinn.

## 4. Samsung: Schlafende Apps und Akku

- Samsung schränkt für „schlafende“ Apps **Jobs, Alarme und Foreground Services** ein („A bucket restriction applies to any sleeping applications, and features such as Job, Alarm, and Foreground-service are restricted“). Apps, die lange nicht genutzt werden (derzeit 16 Tage), kommen in den **Tiefschlaf** ([Samsung App Management][sam-dev]).
- dontkillmyapp.com stuft Samsung als besonders aggressiv ein und empfiehlt, die App aus „Schlafende/Tief schlafende Apps“ zu nehmen und zu „Nie schlafende Apps“ hinzuzufügen ([dontkillmyapp Samsung][dkma]).
- Samsung bietet einen Deeplink in genau diesen Bildschirm (Hintergrundnutzungslimits, Typ 2 = „Nie schlafend“) ([Samsung App Management][sam-dev]). Den kann die App im Setup-Assistenten öffnen.

**Einmalige manuelle Einstellungen am Kindgerät (Checkliste):**
1. *Einstellungen › Akku › Hintergrundnutzungslimits › Nie schlafende Apps* → App hinzufügen ([Samsung Support][sam-sleep], [Samsung Dev][sam-dev]).
2. *Apps › [App] › Akku* → **Nicht eingeschränkt**. Alternativ per `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`-Dialog aus der App, was die meisten Apps auslösen dürfen ([Doze & Standby][doze]). Die Play-Einschränkung dafür entfällt beim Sideload.
3. Optional: „Nicht verwendete Apps in Standby versetzen“ ausschalten ([Samsung Support][sam-sleep]).
4. *Benachrichtigungen › Nicht stören › Alarme und Töne* → **Alarme** an ([Samsung Support DND][sam-dnd]).
5. Alarm-Lautstärke > 0. Die App kann beim Start warnen, wenn `getStreamVolume(STREAM_ALARM) == 0`.
6. Full-Screen-Benachrichtigungen erlauben (*Spezieller App-Zugriff*), falls `canUseFullScreenIntent()` false liefert ([AOSP FSI][fsi]).
7. Benachrichtigungen erlauben (`POST_NOTIFICATIONS`, Android 13+).

Die App sollte 1, 2, 5, 6 und 7 beim Start selbst prüfen und einen Setup-Bildschirm anzeigen.

## 5. PWA / Web Push: nein

- **Lokales Zeitplanen gibt es im Web nicht.** Die „Notification Triggers“-API (`TimestampTrigger`) hat nur einen Origin Trial durchlaufen, Status „Launch: not started“ ([Chrome Notification Triggers][nt]). Ohne sie müsste jeder Alarm und jede 5-Minuten-Wiederholung als **Push vom Server** kommen, also offline unmöglich.
- **Zustellung in Doze:** Nur mit hoher Priorität/Urgency versucht FCM die sofortige Zustellung („attempts to deliver high priority messages immediately even if the device is in Doze“), und FCM kann sie herabstufen ([Firebase message priority][fcm-prio]). Das ist ein Versuch, keine Garantie.
- **Kein Alarm-Ton:** Die Web-Notification-Option `sound` wird von keinem Browser unterstützt ([MDN Notification.sound, Mirror][mdn-sound]). Der Ton ist der normale Benachrichtigungston von Chrome, also nicht der Alarm-Stream, und er unterliegt Stumm/DND. Es gibt keinen Vollbild-Alarm und keinen Dauerton.
- Fazit: Fürs **Elterngerät** (eine Benachrichtigung beim Alarm) ist Web Push brauchbar, für den Alarm am **Kindgerät** nicht.

## 6. Capacitor-Hybrid

- **`@capacitor/local-notifications` reicht nicht.** Es plant mit `allowWhileIdle`, das in Doze **nur ca. 1× pro 9 Minuten pro App** feuert, und braucht ab Android 12 `SCHEDULE_EXACT_ALARM` für Exaktheit ([Capacitor Local Notifications][cap-ln]). Der Ton ist ein Notification-Channel-Ton, kein selbst gesteuerter Alarm-Stream mit Wiederholung. Im Forum gibt es zudem Berichte über verspätete Zustellung bei geschlossener App ([Ionic Forum][ionic-late]) **[Sekundärquelle]**.
- **Capacitor + eigenes Kotlin-Plugin ist gleich zuverlässig.** Die Zuverlässigkeit hängt allein an der nativen Schicht (AlarmManager, Receiver, FGS, MediaPlayer), und die läuft ohne WebView. Die WebView wird beim Alarm gar nicht gebraucht. Voraussetzung ist, dass der Sitzungszustand (nächster Alarmzeitpunkt) **nativ** gespeichert wird (z. B. SharedPreferences/DataStore), damit Boot-Receiver und Alarm-Kette ohne JS laufen.
- Trade-off: Vue-UI und Code-Teilung mit dem Elterngerät gegen eine zweite Build-Schicht. Der Alarm-Kern ist in beiden Varianten das gleiche Stück Kotlin (ca. ein Receiver, ein Service, ein Scheduler).

## Empfehlung

1. **Alarm-Kern nativ in Kotlin**, unabhängig von der UI-Wahl: `AlarmScheduler` (`setAlarmClock`), `AlarmReceiver` → `AlarmService` (FGS `systemExempted`, `MediaPlayer` mit `USAGE_ALARM`, Notification mit Full-Screen-Intent und Stopp-Aktion), `BootReceiver` (nur neu planen). Vorwarnung und Alarm sind je ein eigener `setAlarmClock`, jede Wiederholung plant die nächste (+5 min).
2. **Manifest:** `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` (maxSdk 32), `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `WAKE_LOCK`.
3. **UI-Hülle:** Entweder native Kotlin/Compose oder Capacitor/Vue mit eigenem Plugin. Beide sind für den Alarm gleich gut. **Nicht** `@capacitor/local-notifications` für den Alarm nutzen, **keine** PWA fürs Kindgerät.
4. **Setup-Assistent** in der App für die Checkliste aus Abschnitt 4 (Deeplinks, Statusprüfungen). Beim App-Start eine Warnung zeigen, wenn eine Voraussetzung fehlt.
5. **Testen** (für „Teststrategie“ in der Map): `adb shell dumpsys deviceidle force-idle` (Doze erzwingen), Bildschirm aus und mehrere Stunden warten, eine andere App im Vordergrund, DND an, Neustart während einer laufenden Sitzung.

Offene Punkte: Ob One UI `USE_FULL_SCREEN_INTENT` bei Sideload automatisch erteilt und ob die DND-Ausnahme „Alarme“ auf dem konkreten Tablet standardmäßig an ist, lässt sich nur am Gerät prüfen.

[alarms]: https://developer.android.com/develop/background-work/services/alarms
[am]: https://developer.android.com/reference/android/app/AlarmManager
[amc]: https://developer.android.com/reference/androidx/core/app/AlarmManagerCompat
[a14-exact]: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
[alarm439]: https://github.com/gdelataillade/alarm/issues/439
[esper]: https://www.esper.io/blog/android-13-exact-alarm-api-restrictions
[fsi]: https://source.android.com/docs/core/permissions/fsi-limits
[fgs-bg]: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
[fgs-req]: https://developer.android.com/about/versions/14/changes/fgs-types-required
[fgs-types]: https://developer.android.com/develop/background-work/services/fgs/service-types
[a15-fgs]: https://developer.android.com/about/versions/15/changes/foreground-service-types
[a17-audio]: https://developer.android.com/about/versions/17/changes/bg-audio
[a17-rn]: https://developer.android.com/about/versions/17/release-notes
[doze]: https://developer.android.com/training/monitoring-device-state/doze-standby
[sam-dev]: https://developer.samsung.com/mobile/app-management.html
[sam-sleep]: https://www.samsung.com/us/support/answer/ANS00088422/
[sam-dnd]: https://www.samsung.com/us/support/answer/ANS10002889
[dkma]: https://dontkillmyapp.com/samsung
[nt]: https://developer.chrome.com/docs/web-platform/notification-triggers
[fcm-prio]: https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-priority
[mdn-sound]: https://reference.codeproject.com/dom/Notification/sound
[cap-ln]: https://capacitorjs.com/docs/apis/local-notifications
[ionic-late]: https://forum.ionicframework.com/t/android-capacitor-local-notifications-issues-scheduled-notifications-are-not-delivered-if-the-app-has-been-closed-for-more-than-2-hours/235387
[flet]: https://flet.dev/docs/services/notifications/types/notificationchannel/
[nagios]: https://github.com/pgmac-net/nagios-android/issues/109
