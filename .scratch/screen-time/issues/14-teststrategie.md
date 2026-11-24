# Wie wird getestet, insbesondere das Alarmverhalten?

Type: grilling
Status: resolved
Assignee: Dawid Rusin
Blocked by: -

## Question

Fest steht: JVM-Tests (`domain`, inkl. Sommerzeit) und Lint sind das Gate vor dem Release ([Wie wird die APK gebaut, signiert und veröffentlicht?](09-build-release-apk.md)). Offen ist der Rest:
- Welche Ebenen werden automatisch getestet: `domain`, Server (Ktor-Testhost, SQLite), Sync-Protokoll ([Wie werden offline gepufferte Start- und Stopp-Ereignisse mit dem Server zusammengeführt?](10-sync-offline-ereignisse.md)), App (Robolectric, instrumentiert)?
- Wie wird geprüft, dass der Alarm im Doze, im Standby und bei einer anderen App im Vordergrund klingelt ([Wie klingelt ein Alarm auf Samsung-Android zuverlässig im Hintergrund und Standby?](01-alarm-zuverlaessig-android.md))? `adb`-Doze-Befehle, Emulator, manuelle Checkliste am echten Samsung-Tablet, vor jedem Release oder nur bei Änderungen am Alarmkern?
- Wird der Push-Weg (Server → ntfy → UnifiedPush → App) Ende-zu-Ende getestet, und wie?
- Was davon läuft in CI, was manuell?

## Answer

Im Grilling mit dem Nutzer am 2026-11-24 entschieden.

1. **Automatische Ebenen, alle im bestehenden Gate** (siehe [Wie wird die APK gebaut, signiert und veröffentlicht?](09-build-release-apk.md)):
   - `domain`: Unit-Tests für alle Regeln aus [Wie verhalten sich Kontingent und Sitzung in den Randfällen?](04-domaenenregeln-randfaelle.md) (Mitternacht, Sommerzeit, Tagesanpassung, Überziehungsgrenze, Vorwarnung).
   - `server`: Ktor-`testApplication` gegen eine echte SQLite-Datei im Temp-Ordner, ohne Datenbank-Mocks.
   - Sync-Protokoll: Szenario-Tests von der Outbox der App-Logik gegen den echten Server im Testhost (offline-Start, das frühere Ende gewinnt, doppelte Batches), siehe [Wie werden offline gepufferte Start- und Stopp-Ereignisse mit dem Server zusammengeführt?](10-sync-offline-ereignisse.md).
   - App: Robolectric-Tests, die prüfen, dass der Alarmkern je nach Zustand den richtigen `setAlarmClock`-Zeitpunkt plant oder storniert, plus ViewModel-Tests. Keine Compose-UI-Tests.
2. **Injizierte Uhr ist Pflicht.** Server, `domain` und App bekommen Wanduhr und monotone Uhr injiziert, und Tests stellen die Zeit vor, statt zu warten. Ein Architekturtest (z. B. Konsist) verbietet direkte Aufrufe wie `System.currentTimeMillis()` oder `Clock.systemUTC()` im Produktivcode. Einen eigenen Lint-Check gibt es nicht.
3. **Alarm am echten Gerät: manuelle Checkliste** in `docs/testing/alarm-checkliste.md`, durchgegangen am echten Samsung-Tablet:
   - Bildschirm aus und `adb shell dumpsys deviceidle force-idle`: der Alarm klingelt.
   - Eine fremde App (Video, Spiel) im Vordergrund: der Vollbild-Alarm erscheint, der Ton kommt über den Alarm-Stream.
   - „Nicht stören“ an: der Alarm klingelt trotzdem.
   - Wiederholung nach 5 min, Vorwarnung, Automatischer Stopp.
   - Neustart des Tablets während einer Sitzung: der Alarm wird neu geplant.
   - Push-Weg (siehe Punkt 4).

   Die Checkliste wird **nur bei relevanten Änderungen** durchgegangen: am Alarmkern, an Manifest oder Berechtigungen, an `targetSdk`, oder nach einem großen One-UI-/Android-Update am Tablet. Nicht vor jedem Release. Debug-Builds bekommen einen versteckten Knopf „Testsitzung mit 2 min Kontingent“, damit ein Durchlauf nur Minuten dauert.
4. **Push-Weg ohne automatischen Ende-zu-Ende-Test.** Push ist nur ein Signal, die Korrektheit hängt nicht daran. Ein Servertest prüft gegen einen Fake-HTTP-Endpunkt, dass bei einem Ereignis die richtige verschlüsselte Nachricht an ntfy geht. Ein App-Test prüft, dass ein empfangenes Signal einen Sync auslöst. Der echte Weg Server → ntfy → UnifiedPush → App steht auf der manuellen Checkliste und wird zusätzlich nach jedem Server-Update kurz geprüft: eine Tagesanpassung am Elterngerät muss innerhalb weniger Sekunden am Kindgerät ankommen.
5. **Keine instrumentierten Tests in v1**, auch nicht nightly auf einem Emulator. Ein Emulator prüft genau das Samsung-Verhalten nicht, auf das es ankommt, und kostet Pflege. Robolectric plus die manuelle Checkliste decken das Risiko ab.
