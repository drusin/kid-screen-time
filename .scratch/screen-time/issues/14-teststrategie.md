# Wie wird getestet, insbesondere das Alarmverhalten?

Type: grilling
Status: open
Blocked by: -

## Question

Fest steht: JVM-Tests (`domain`, inkl. Sommerzeit) und Lint sind das Gate vor dem Release ([Wie wird die APK gebaut, signiert und veröffentlicht?](09-build-release-apk.md)). Offen ist der Rest:
- Welche Ebenen werden automatisch getestet: `domain`, Server (Ktor-Testhost, SQLite), Sync-Protokoll ([Wie werden offline gepufferte Start- und Stopp-Ereignisse mit dem Server zusammengeführt?](10-sync-offline-ereignisse.md)), App (Robolectric, instrumentiert)?
- Wie wird geprüft, dass der Alarm im Doze, im Standby und bei einer anderen App im Vordergrund klingelt ([Wie klingelt ein Alarm auf Samsung-Android zuverlässig im Hintergrund und Standby?](01-alarm-zuverlaessig-android.md))? `adb`-Doze-Befehle, Emulator, manuelle Checkliste am echten Samsung-Tablet, vor jedem Release oder nur bei Änderungen am Alarmkern?
- Wird der Push-Weg (Server → ntfy → UnifiedPush → App) Ende-zu-Ende getestet, und wie?
- Was davon läuft in CI, was manuell?
