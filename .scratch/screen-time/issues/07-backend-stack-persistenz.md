# Welcher Backend-Stack, welche Persistenz und welche API-Form?

Type: grilling
Status: resolved
Assignee: Dawid Rusin
Blocked by: 02, 06

## Question

Womit wird der Server im Homelab gebaut (z. B. Node/TS, Java/Quarkus/Spring, Kotlin/Ktor), welche Persistenz nutzt er (SQLite in einem Volume vs. Postgres), und welche API-Form (REST/JSON, Echtzeitkanal)? Die Antwort muss zur gewählten Push-Lösung und zur App-Architektur passen und als einzelner Docker-Container (oder kleines Compose-Setup) laufen.

Rahmen aus [Wie werden Geräte gekoppelt und der Server abgesichert?](05-kopplung-sicherheit.md): Geräte-Tokens (nur Hash gespeichert), Rollenprüfung pro Endpunkt, globales Rate-Limit, ein Admin-Webformular und ein WebPush-verschlüsselter (RFC 8291) Push-Payload an ntfy. Der Stack muss das gut unterstützen.

Rahmen aus [Welche App-Architektur und welcher Stack?](06-app-architektur-stack.md): Die App ist native Kotlin/Compose mit einem reinen Kotlin-Modul `domain` für alle Zeit- und Kontingentregeln, und sie nutzt Ktor Client + kotlinx.serialization. Ein Kotlin-Backend könnte `domain` und die DTOs teilen. Das ist ein Argument, keine Vorentscheidung.

## Answer

Im Grilling mit dem Nutzer am 2026-11-24 entschieden.

1. **Server: Kotlin + Ktor.** `domain` (Regeln) und `api` (DTOs) werden mit der App geteilt, damit die Zeit- und Kontingentregeln nur einmal existieren. Verworfen: Java/Quarkus/Spring, weil die Regeln dann nur über Kotlin-Interop geteilt werden könnten, und Node/TS, weil `domain` dann doppelt gepflegt werden müsste, was gerade bei Sommerzeit riskant ist.
2. **Persistenz: SQLite** als Datei in einem Docker-Volume, mit Migrationen. Die Bibliothek (z. B. Exposed oder JDBC + Flyway) wird bei der Umsetzung gewählt. Postgres wurde verworfen, weil die Last vernachlässigbar ist und ein zweiter Dienst nichts bringt.
3. **API: REST/JSON mit Stand-Snapshot.** Der Server liefert pro Gerät den vollständigen Stand (Tageskontingent, Verbrauch, laufende Sitzung, Einstellungen wie Vorwarnschwelle und Überziehungsgrenze, Serverzeit) und keine Deltas. Es gibt keinen eigenen Echtzeitkanal (WebSocket/SSE). ntfy/UnifiedPush sendet nur das Signal „neu abgleichen“. Abgeglichen wird nach jedem Push, beim App-Start und beim Entsperren.
4. **Container:** Ein Compose-Setup mit zwei Containern, dem eigenen Server und dem offiziellen ntfy-Image, beide hinter dem Reverse Proxy. Der Server pusht über das Compose-Netz an ntfy.
5. **Repo:** Monorepo mit einem Gradle-Build und den Modulen `domain` (reines Kotlin), `api` (DTOs/Pfade, reines Kotlin, kotlinx.serialization), `app` (Android) und `server` (Ktor).
6. **Zeitgesteuerte Ereignisse:** Der Server berechnet mit `domain` den nächsten fälligen Zeitpunkt (Restzeit 0, Überziehungsgrenze/Automatischer Stopp, Mitternacht) und plant ihn im Prozess. Bei jeder Änderung und beim Neustart rechnet er neu aus der DB. Es gibt keine Job-Queue, keinen Cron und keine gespeicherten Planungen. Der Server verschickt **alle Eltern-Benachrichtigungen**, auch wenn das Tablet offline ist. Eine offline gestartete Sitzung kennt er erst nach dem Sync.
7. **Datenmodell-Grundsätze:**
   - Alles hängt an einer `child_id`, auch wenn es erst ein Kind gibt.
   - Die Wochenvorlage ist mit „gültig ab“-Datum versioniert, das Tageskontingent ergibt sich aus der Version zu Tagesbeginn plus den Tagesanpassungen. So braucht es keinen Mitternachts-Job, der etwas festschreibt.
   - Tagesanpassungen werden nur angehängt.
   - Eine Sitzung ist eine Zeile mit Start, Ende (leer, solange sie läuft) und Grund für das Ende (Kind/Automatischer Stopp). Die Mitternachtsaufteilung wird beim Lesen berechnet.
8. **Admin-Webformular:** serverseitig gerendert mit kotlinx.html im Ktor-Server unter `/admin`, ohne JS und ohne eigenen Container. Der Reverse Proxy beschränkt den Pfad auf das Heimnetz.
9. **Laufzeit:** JVM-Fat-JAR auf `eclipse-temurin:25-jre` (aktuelles LTS, Java 25). GraalVM Native wurde verworfen, weil die Builds dafür langsam und anfällig sind.
10. **Ausgelagert:** Das Sync-Protokoll für offline gepufferte Ereignisse bekommt ein eigenes Ticket: [Wie werden offline gepufferte Start- und Stopp-Ereignisse mit dem Server zusammengeführt?](10-sync-offline-ereignisse.md). Ebenso der Betrieb im Homelab: [Wie wird der Server im Homelab betrieben und gesichert?](11-deployment-homelab.md).
