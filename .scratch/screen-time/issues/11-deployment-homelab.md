# Wie wird der Server im Homelab betrieben und gesichert?

Type: grilling
Status: open
Blocked by: 07

## Question

Der Stack steht ([Welcher Backend-Stack, welche Persistenz und welche API-Form?](07-backend-stack-persistenz.md)): Ktor-Server auf `eclipse-temurin:25-jre` mit SQLite im Volume, dazu ntfy als zweiter Container in einem Compose-Setup. Offen ist der Betrieb:
- **Reverse Proxy:** Subdomains oder Pfade für Server und ntfy? Wie wird `/admin` auf das Heimnetz beschränkt? Annahme prüfen: Es gibt einen Reverse Proxy mit TLS und eine Domain.
- **Secrets und Konfiguration:** Admin-Passwort, Familien-Zeitzone und ntfy-URL (Env-Variablen vs. Docker Secrets).
- **ntfy-Konfiguration:** ohne Benutzerverwaltung, aber wie gegen Missbrauch als offenes Relay schützen (Topics anlegen, Rate-Limits, Cache-Dauer)?
- **Backups:** Wie und wohin wird die SQLite-Datei gesichert (`VACUUM INTO`, Zeitplan, Aufbewahrung)? Wie sieht die Wiederherstellung aus?
- **Image und Updates:** Wo wird das Server-Image gebaut und abgelegt (z. B. GHCR), und wie kommt eine neue Version ins Homelab?
