# Welche App-Architektur und welcher Stack?

Type: grilling
Status: resolved
Assignee: Dawid Rusin
Blocked by: 01, 02, 03

## Question

Aufbauend auf den Recherchen zu Alarm, Push und APK-Verteilung: Ist es **eine native Kotlin-App** mit beiden Rollen, ein **Hybrid** (Vue/TS in Capacitor mit nativem Alarm-Plugin) oder eine **native Kind-App plus Eltern-PWA**? Kriterien: Zuverlässigkeit des Alarms, Wartbarkeit für den Nutzer (JS/TS/Vue, Java), „native-like“-Gefühl, Aufwand für Build und Updates.

## Answer

Im Grilling mit dem Nutzer am 2026-11-24 entschieden.

1. **Eine native App in Kotlin + Jetpack Compose** für beide Rollen (eine APK, die Rolle kommt aus der Kopplung). Verworfen: der **Capacitor/Vue-Hybrid**, weil Alarm, Sitzungszustand, Push-Empfang und Token-Speicher ohnehin nativ sein müssen ([Wie klingelt ein Alarm auf Samsung-Android zuverlässig im Hintergrund und Standby?](01-alarm-zuverlaessig-android.md)). Vue bliebe nur für den einfachen Teil, und dafür wären eine Brücke, doppelter Zustand und eine zweite Toolchain nötig. Ebenfalls verworfen: **native Kind-App + Eltern-PWA**, weil Web Push im Doze unzuverlässig ist und es einen zweiten Kopplungsweg bräuchte. Dass der Nutzer Kotlin weniger gut selbst warten kann als Vue, wiegt gering, weil Claude baut.
2. **`minSdk 33`** (Android 13+): Alle Geräte erfüllen das, und `USE_EXACT_ALARM` gibt es ab API 33.
3. **Nur Deutsch**, die Texte trotzdem in `strings.xml`.
4. **Material 3 mit Dynamic Color.** Für die Zustände der Restzeit-Anzeige am Kindgerät (läuft, Vorwarnung, Überziehung) gibt es eigene Farben, Details im UI-Prototyp.
5. **Gradle-Module:** `domain` ist reines Kotlin ohne Android-Abhängigkeit. Es enthält Tageskontingent, Restzeit, Überziehung, die Zeitpunkte für Vorwarnung und Alarm, den Automatischen Stopp und die Aufteilung über Mitternacht, und es wird gegen Sommerzeit-Grenzen getestet. `app` ist die Android-App. Wird das Backend Kotlin, kann es `domain` wiederverwenden (Hinweis für [Welcher Backend-Stack, welche Persistenz und welche API-Form?](07-backend-stack-persistenz.md)).
6. **Bibliotheken:** Room (lokaler Stand und Puffer für offline angefallene Start/Stopp-Ereignisse), Ktor Client + kotlinx.serialization (REST), manuelle Dependency Injection (kein Hilt), Coroutines/Flow, UnifiedPush-Android-Connector.
7. **Online-Verhalten:** Am Elterngerät funktionieren alle Aktionen (Tagesanpassung, Wochenvorlage, Koppeln, Entkoppeln) **nur online**. Offline zeigt es den letzten bekannten Stand nur zum Lesen, mit dem Hinweis „offline“. Am Kindgerät bleibt Start/Stopp offline-fähig.
8. **Einrichtungs-Checkliste am Kindgerät:** Sie läuft bei jedem App-Start und prüft Exakte Alarme, Full-Screen-Intent, Benachrichtigungen, Akku „Nicht eingeschränkt“, Alarmlautstärke > 0 und den verbundenen UnifiedPush-Verteiler. Fehlendes erscheint rot mit einem direkten Link in die Systemeinstellung. Was sich nicht prüfen lässt („Nie schlafende Apps“, Alarme bei Nicht stören), steht als Hinweis zum Abhaken da. Fehlt etwas Prüfbares, zeigt der Hauptbildschirm ein Warnbanner. Die Eltern werden vorerst nicht benachrichtigt (siehe Fog „Lebenszeichen“).
9. **UnifiedPush-Verteiler:** Auf allen Geräten läuft die **ntfy-Android-App** (F-Droid/Obtainium), eingerichtet auf den Homelab-ntfy-Server. Verworfen: ein eingebetteter Verteiler (FCM: Abhängigkeit von Google, eigene Dauerverbindung: Foreground-Service und Akkuverbrauch). Auf den Eltern-Handys gibt es dazu einen Einrichtungshinweis.
