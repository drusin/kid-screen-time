# Wie wird der Server im Homelab betrieben und gesichert?

Type: grilling
Status: resolved
Assignee: Dawid Rusin
Blocked by: 07

## Question

Der Stack steht ([Welcher Backend-Stack, welche Persistenz und welche API-Form?](07-backend-stack-persistenz.md)): Ktor-Server auf `eclipse-temurin:25-jre` mit SQLite im Volume, dazu ntfy als zweiter Container in einem Compose-Setup. Offen ist der Betrieb:
- **Reverse Proxy:** Subdomains oder Pfade für Server und ntfy? Wie wird `/admin` auf das Heimnetz beschränkt? Annahme prüfen: Es gibt einen Reverse Proxy mit TLS und eine Domain.
- **Secrets und Konfiguration:** Admin-Passwort, Familien-Zeitzone und ntfy-URL (Env-Variablen vs. Docker Secrets).
- **ntfy-Konfiguration:** ohne Benutzerverwaltung, aber wie gegen Missbrauch als offenes Relay schützen (Topics anlegen, Rate-Limits, Cache-Dauer)?
- **Backups:** Wie und wohin wird die SQLite-Datei gesichert (`VACUUM INTO`, Zeitplan, Aufbewahrung)? Wie sieht die Wiederherstellung aus?
- **Image und Updates:** Wo wird das Server-Image gebaut und abgelegt (z. B. GHCR), und wie kommt eine neue Version ins Homelab?

## Answer

Im Grilling mit dem Nutzer am 2026-11-24 entschieden, nach einem Blick auf den bestehenden Homelab-Stack (`dawid@zotacserver:~/docker`).

**Ist-Zustand im Homelab:** Traefik v3 mit Docker-Provider auf dem externen Netz `proxy`. Die Standardregel ist `Host(<compose-service>.rusin.xyz)`, die Zertifikate holt Let's Encrypt per TLS-Challenge. Die Annahme aus der Map (Reverse Proxy mit TLS und Domain) ist damit bestätigt. Konvention: pro Bereich ein Ordner in `~/docker` (Git-Repo), darin eine `docker-compose.yaml` mit `include`. Images haben eine feste Version, Daten liegen als Bind-Mount unter `/media/ssd/docker-container-data/<dienst>/`, dazu `user: 1000:1000` und `TZ=Europe/Berlin`. Gestartet wird über `docker-up-all.sh`. Es gibt keine Docker Secrets und bisher kein Backup.

1. **Stack:** Ein eigener Ordner `~/docker/kidscreentime/` mit `docker-compose.yaml` (`include` auf `server.docker-compose.yaml` und `ntfy.docker-compose.yaml`). Beide Container hängen am Netz `proxy` und an einem internen Netz. Daten liegen unter `/media/ssd/docker-container-data/kidscreentime/{server,ntfy}`, mit `user: 1000:1000` und `restart: unless-stopped`. Das Repo liefert dafür eine Beispiel-Compose-Datei, der Nutzer übernimmt sie in sein `~/docker`-Repo.
2. **Subdomains:** `kidscreentime.rusin.xyz` (Server) und `ntfy.rusin.xyz` (ntfy). Sie ergeben sich aus den Compose-Servicenamen und der `defaultRule`. Pfade wurden verworfen, weil ntfy die Root einer Domain erwartet.
3. **`/admin` nur im Heimnetz:** Der Ktor-Server hat für `/admin` einen **eigenen Listener auf einem zweiten Port**. Dieser ist nur als Host-Port publiziert (z. B. `8790`) und hat **keine Traefik-Route**. Erreichbar ist er unter `http://zotacserver:8790/admin`. Der Router leitet nur 80/443 weiter, über `kidscreentime.rusin.xyz` ist `/admin` also nicht erreichbar. Das Admin-Passwort bleibt die zweite Hürde. Im LAN läuft das Formular bewusst über HTTP. Verworfen wurde Traefik `ipAllowList`: Über IPv6 (`[::]:443`) und den `docker-proxy` kommen Verbindungen mit der Docker-Gateway-IP an, und Hairpin-NAT verfälscht die Quell-IP ebenfalls. Eine Allowlist für private Netze ließe dann Fremde durch. Damit wird Punkt 8 aus [Welcher Backend-Stack, welche Persistenz und welche API-Form?](07-backend-stack-persistenz.md) präzisiert.
4. **ntfy als Relay absichern:** Die Traefik-Route für `ntfy.rusin.xyz` lässt nur `GET`, `HEAD` und `OPTIONS` durch. Abonnieren über JSON-Stream und WebSocket bleibt möglich, Publishen von außen nicht. Der Server publisht intern über `http://ntfy:80`. In ntfy gibt es keine Benutzer und kein Token. Konfiguriert sind `cache-duration: 12h`, keine Attachments, kein `upstream-base-url` und die Standard-Rate-Limits. *Bei der Umsetzung zu prüfen:* dass die ntfy-App und die UnifiedPush-Registrierung keine schreibenden Requests nach außen brauchen.
5. **Konfiguration und Secrets:** `ADMIN_PASSWORD` steht in `~/docker/kidscreentime/.env` (`chmod 600`, in der `.gitignore` des `~/docker`-Repos). Die nicht geheimen Werte stehen direkt in der Compose-Datei: `FAMILY_TIMEZONE=Europe/Berlin`, `PUBLIC_URL=https://kidscreentime.rusin.xyz`, `NTFY_PUBLIC_URL=https://ntfy.rusin.xyz`, `NTFY_INTERNAL_URL=http://ntfy:80`. `FAMILY_TIMEZONE` ist bewusst getrennt von `TZ`, das nur Logs und Container-Uhr betrifft. Fehlt ein Pflichtwert, startet der Server nicht. Keine Docker Secrets, die nutzt das Homelab sonst auch nicht.
6. **Image:** Derselbe Tag `vX.Y.Z`, der die APK baut ([Wie wird die APK gebaut, signiert und veröffentlicht?](09-build-release-apk.md)), baut und pusht auch `ghcr.io/drusin/kid-screen-time-server:X.Y.Z`. Das Image ist `linux/amd64`, öffentlich und lässt sich ohne Login pullen. Es hat dasselbe Gate wie die APK und kein `latest`-Tag.
7. **Updates:** Manuell wie bei allen anderen Diensten: Version in der Compose-Datei hochsetzen, dann `docker compose pull && docker compose up -d`. Migrationen laufen beim Start. **Zuerst wird der Server aktualisiert, dann die App.** Der Server bleibt zur jeweils vorigen App-Version kompatibel. Keine automatischen Updates (Komodo/Watchtower).
8. **Backups:** Für diese Map **out of scope** (Entscheidung des Nutzers).
