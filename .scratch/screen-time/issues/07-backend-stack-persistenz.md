# Welcher Backend-Stack, welche Persistenz und welche API-Form?

Type: grilling
Status: open
Blocked by: 02, 06

## Question

Womit wird der Server im Homelab gebaut (z. B. Node/TS, Java/Quarkus/Spring, Kotlin/Ktor), welche Persistenz nutzt er (SQLite in einem Volume vs. Postgres), und welche API-Form (REST/JSON, Echtzeitkanal)? Die Antwort muss zur gewählten Push-Lösung und zur App-Architektur passen und als einzelner Docker-Container (oder kleines Compose-Setup) laufen.

Rahmen aus [Wie werden Geräte gekoppelt und der Server abgesichert?](05-kopplung-sicherheit.md): Geräte-Tokens (nur Hash gespeichert), Rollenprüfung pro Endpunkt, globales Rate-Limit, ein Admin-Webformular und ein WebPush-verschlüsselter (RFC 8291) Push-Payload an ntfy. Der Stack muss das gut unterstützen.

Rahmen aus [Welche App-Architektur und welcher Stack?](06-app-architektur-stack.md): Die App ist native Kotlin/Compose mit einem reinen Kotlin-Modul `domain` für alle Zeit- und Kontingentregeln, und sie nutzt Ktor Client + kotlinx.serialization. Ein Kotlin-Backend könnte `domain` und die DTOs teilen. Das ist ein Argument, keine Vorentscheidung.
