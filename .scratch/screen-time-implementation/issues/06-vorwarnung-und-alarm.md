# 06: Vorwarnung und Alarm am Kindgerät

**What to build:** Während einer Sitzung kommt bei Erreichen der Vorwarnschwelle ein kurzer Ton mit Snackbar. Bei Restzeit 0 klingelt ein Vollbild-Alarm („Zeit ist um!“, Überziehung, Knopf „Stopp“, „Ton aus · klingelt in 5 min wieder“), auch im Standby/Doze, bei „Nicht stören“ und über anderen Apps. Er wiederholt sich alle 5 Minuten, bis das Kind stoppt. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Modul `domain`“ (nächste fällige Zeitpunkte) und „Alarmkern“.

**Blocked by:** 05 Sitzung starten und stoppen

**Status:** done

- [x] `domain` liefert die nächsten fälligen Zeitpunkte. Die Vorwarnung kommt höchstens einmal pro Sitzung, sofort, wenn die Restzeit schon unter der Schwelle liegt. Der Alarm kommt bei Restzeit 0 (sofort, wenn ≤ 0) und wiederholt sich fest alle 5 Minuten.
- [x] Der Alarmkern plant mit `AlarmManager.setAlarmClock` und `USE_EXACT_ALARM`. Bei jedem Start, Stopp und Sync storniert er und plant neu.
- [x] Beim Klingeln läuft ein kurzer Foreground Service (Typ `systemExempted`), der Ton kommt über den Alarm-Stream, dazu ein Full-Screen-Intent.
- [x] „Stopp“ im Vollbild beendet die Sitzung über die Outbox. „Ton aus“ beendet nur das aktuelle Klingeln, die nächste Wiederholung bleibt geplant.
- [x] Ein Boot-Receiver plant nach einem Neustart neu.
- [x] Ist die Restzeit nach einem Sync ≤ 0, klingelt der Alarm sofort.
- [x] Der Debug-Build hat einen versteckten Knopf „Testsitzung mit 2 min Kontingent“.
- [x] Robolectric-Tests prüfen, dass der Alarmkern bei Start, Stopp, Sync mit geänderter Restzeit und Boot die richtigen Zeitpunkte plant oder storniert.
- [x] Die manuelle Alarm-Checkliste liegt als Dokument im Repo (Punkte siehe Spezifikation v1, „Manuell“).

## Notizen zur Umsetzung

**`domain`:** `KindStand.faelligeZeitpunkte(jetzt, verlauf)` (`FaelligeZeitpunkte.kt`) liefert `FaelligeZeitpunkte(liste)` aus `FaelligerZeitpunkt(Ausloeser, Instant)`, sortiert; `naechster` und `faelligBis(jetzt)`. `Ausloeser` ist ein Enum (`VORWARNUNG`, `ALARM`); Ticket 07 und 11 ergänzen Einträge und Regeln dort. `Alarmverlauf(vorwarnungGegeben, letzterAlarm)` ist der Teil, den `domain` nicht kennt und den das Gerät je Sitzung merkt. Regeln: nur bei laufender Sitzung; Vorwarnung bei Restzeit = Schwelle, sofort bei Restzeit zwischen 0 und Schwelle, nie wieder, wenn schon gegeben (auch nicht nach einem Bonus); Alarm bei Restzeit 0, sofort bei Restzeit 0, danach `letzterAlarm + 5 min` (überfällig = sofort). Ein Klingeln vor dem letzten Erreichen von Restzeit 0 zählt für die Wiederholung nicht (Restzeit stieg zwischendurch über 0). Zeitpunkte nach Tagesende gibt es nicht (Mitternacht ist Ticket 11).

**Alarmkern (`app/.../alarm`):** `AlarmKern` plant nur den frühesten Zeitpunkt mit einem `setAlarmClock` (ein `PendingIntent` auf `AlarmReceiver`, wird ersetzt). Beim Klingeln läuft `trigger()`: alles Fällige ausführen, in `AlarmMemory` (SharedPreferences, je Sitzung) vermerken, neu planen. Sind Vorwarnung und Alarm zugleich fällig, klingelt nur der Alarm. Wiederholung zählt ab dem tatsächlichen Klingeln (`jetzt`), nicht ab dem Soll-Zeitpunkt, damit nach einem späten Alarm nicht sofort wieder geklingelt wird. `AlarmScheduler` kapselt den `AlarmManager`, `AlarmEffects` Ton und Vollbild (Tests nehmen `RecordingAlarmEffects`). Ein klingelnder Alarm wird beendet, sobald die lokale Restzeit wieder über 0 liegt oder keine Sitzung mehr läuft (Neuberechnung nach Sync/Tagesanpassung, Ticket 09).

**Der Hook für Neuplanung:** `AlarmKern.observe` (gestartet in `KidScreenTimeApp.onCreate`) sammelt `ChildRepository.local` (Snapshot plus Outbox) und plant bei jeder Änderung neu. Das deckt Start, Stopp, jeden Sync (auch den Push-ausgelösten aus Ticket 13) und den Prozessstart nach Neustart ab. `ChildRepository` und seine `sync`-Funktion sind unverändert. Ein Sync, der einen anderen Snapshot-Inhalt oder auch nur einen neuen Empfangszeitpunkt speichert, löst die Neuplanung aus (die Zeile ändert sich immer). `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`) plant einmal sicher neu.

**Klingeln:** `AlarmService` (Foreground Service, Typ `systemExempted`, Alarm-Stream über `Ringtone` mit `USAGE_ALARM`, Standard-Alarmton, endet nach 60 s selbst), Notification mit Full-Screen-Intent auf `AlarmActivity` (über Sperrbildschirm, Bildschirm an). Notification-Aktion „Ton aus“. Die Vorwarnung nutzt denselben Service mit kurzem Benachrichtigungston (4 s) über den Alarm-Stream; die Snackbar zeigt der Kind-Bildschirm, wenn er den Wechsel nach `VORWARNUNG` sieht (nicht, wenn er schon in der Vorwarnung geöffnet wird). „Stopp“ im Vollbild geht über `ChildRepository.stopSession` (Outbox) plus Sync, „Ton aus“ beendet nur den Service.

**Manifest:** `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, `RECEIVE_BOOT_COMPLETED`. Die Laufzeit-Abfrage für Benachrichtigungen und das Prüfen von Full-Screen-Intent sind Teil der Einrichtungs-Checkliste (Ticket 15), nicht dieses Tickets.

**Debug-Knopf:** kleiner Textknopf unten auf dem Kind-Bildschirm („versteckt“ = nur im Debug-Build). `TestSession` (Interface in `main`) ersetzt den Stand durch eine lokale Sitzung mit 2 min Kontingent und 1 min Vorwarnschwelle, ohne Server und ohne Outbox. Implementierung `DebugTestSession` in `src/debug`, in `src/release` gibt es nur leere Gegenstücke (`createTestSession`, `DebugTools`). Stopp im Vollbild beendet die Testsitzung.

**Tests:** `FaelligeZeitpunkteTest` (`domain`), `AlarmKernTest` und `AlarmViewModelTest` (Robolectric mit echtem Server über `syncScenario`, `ShadowAlarmManager`). `syncScenario`/`TestContainer` nehmen optional `alarmEffects`. Manuell: `docs/alarm-checkliste.md` (Punkte 9 und 10 gelten erst mit Ticket 13 und 07).

**Für spätere Tickets:** 07 ergänzt `Ausloeser.AUTOMATISCHER_STOPP` in `domain` und die Behandlung in `AlarmKern.trigger` (lokal über `repository.stopSession`-artigen Outbox-Stopp mit Endzeitpunkt). 11 ergänzt einen Mitternachts-Auslöser, der nur neu plant. 12 muss beim Entkoppeln `AlarmKern.replan(null)` bzw. den Snapshot leeren (null storniert alles, löscht `AlarmMemory` und beendet ein Klingeln).

**Nachtrag (Entscheidung der Eltern, 2026-11-25):** Die Vorwarnung kommt nicht mehr höchstens einmal pro Sitzung, sondern einmal je Ende des Kontingents: nach einem Bonus wieder 5 min (Vorwarnschwelle) vor dem neuen Ende, ebenso für das neue Kontingent nach Mitternacht; ein Abzug löst keine zweite aus. `Alarmverlauf.vorwarnungFuer` (statt `vorwarnungGegeben`) merkt sich das Ende, für das gewarnt wurde (ein Klingeln zählt als Vorwarnung für sein Ende); Enden innerhalb 1 s gelten als gleich. Tests: `FaelligeZeitpunkteTest`, `MitternachtUndSommerzeitTest`, `AlarmKernTest`.
