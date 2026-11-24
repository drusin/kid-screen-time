# Betrieb im Homelab

Der Server läuft als Compose-Stack (Server + ntfy) hinter dem vorhandenen Traefik. Die Beispieldateien liegen in `deploy/compose/`; sie sind auf das Homelab `*.rusin.xyz` zugeschnitten (Domain, Datenpfad, Entrypoint `websecure`, Zertifikatsresolver `letsencrypt` bei Bedarf anpassen).

| Datei | Inhalt |
| --- | --- |
| `docker-compose.yaml` | `include` der beiden Dienste, Netze `proxy` (extern) und `kidscreentime` (intern) |
| `server.docker-compose.yaml` | Server: feste Image-Version, Konfiguration, Bind-Mount, Admin-Host-Port, Traefik-Labels |
| `ntfy.docker-compose.yaml` | ntfy: feste Version, Traefik-Route nur `GET`/`HEAD`/`OPTIONS` |
| `ntfy/server.yml` | ntfy-Konfiguration: keine Benutzer, `cache-duration: 12h`, keine Attachments, kein Upstream, Standard-Rate-Limits |
| `.env.example` | Vorlage für `.env` (nur `ADMIN_PASSWORD` ist Pflicht) |

## Erstinstallation

1. Ordner `deploy/compose/` nach `~/docker/kidscreentime/` kopieren (ins `~/docker`-Repo übernehmen, `.env` in dessen `.gitignore`).
2. `cp .env.example .env`, `chmod 600 .env`, `ADMIN_PASSWORD` setzen. Das Passwort steht nur dort.
3. Datenordner anlegen, Besitzer `1000:1000` (die Container laufen als `1000:1000`):
   `mkdir -p /media/ssd/docker-container-data/kidscreentime/{server,ntfy}` und `chown -R 1000:1000 /media/ssd/docker-container-data/kidscreentime`.
4. In `server.docker-compose.yaml` die Image-Version auf das veröffentlichte Release setzen (`ghcr.io/drusin/kid-screen-time-server:X.Y.Z`, ohne `latest`). Das Image muss in GHCR auf „public“ stehen ([Release-Checkliste](release-checkliste.md)).
5. `docker compose up -d`. Prüfen: `docker compose logs kidscreentime` zeigt „Responding at …“; `curl https://kidscreentime.rusin.xyz/health` liefert `{"status":"ok",…}`. Fehlt ein Pflichtwert, startet der Server nicht und nennt ihn im Log.

Konfiguration (Variablen, Pflichtwerte) steht im [README](../README.md). `FAMILY_TIME_ZONE` (Familie) ist bewusst getrennt von `TZ` (nur Logs und Container-Uhr). `base-url` in `ntfy/server.yml` und `NTFY_PUBLIC_URL` müssen gleich sein.

## Bootstrap über das Admin-Formular

Der Admin-Port ist nur als Host-Port veröffentlicht und hat keine Traefik-Route; über die öffentliche Domain ist `/admin` nicht erreichbar.

1. Im Heimnetz `http://zotacserver:8081/admin` öffnen (Formular über HTTP, bewusst nur im LAN).
2. `ADMIN_PASSWORD` eingeben: Das Formular erzeugt einen Kopplungscode für das erste Elterngerät.
3. In der App unter „Gerät koppeln“ Server-URL (`https://kidscreentime.rusin.xyz`) und Code eintragen. Weitere Geräte koppelt ein Elterngerät selbst.

Dasselbe Formular dient zur Wiederherstellung, wenn kein Elterngerät mehr gekoppelt ist.

## Update

Reihenfolge: **zuerst der Server, dann die App.** Der Server bleibt zur jeweils vorigen App-Version kompatibel.

1. Neue Version in `server.docker-compose.yaml` eintragen (Image-Tag).
2. `docker compose pull && docker compose up -d`. Migrationen laufen beim Start.
3. Push-Weg kurz testen (unten).
4. Danach die App über Obtainium aktualisieren.

Automatische Updates (Watchtower o. Ä.) gibt es bewusst nicht. Backups sind für v1 nicht vorgesehen.

## Push-Weg nach jedem Server-Update testen

Auf allen Geräten läuft die ntfy-Android-App als UnifiedPush-Verteiler, eingerichtet auf `https://ntfy.rusin.xyz`.

1. Elterngerät: bei „Heute anpassen“ eine Tagesanpassung buchen (z. B. +5 min).
2. Am Kindgerät (Bildschirm aus) ändert sich die Restzeit innerhalb weniger Sekunden.
3. Kind startet und stoppt eine Sitzung: das Elterngerät aktualisiert sich ohne Neuladen.
4. Bleibt das aus: `docker compose logs kidscreentime ntfy`; prüfen, ob `curl -I https://ntfy.rusin.xyz` antwortet und ob die ntfy-App das Abo `up…` zeigt. Der Server veröffentlicht intern über `http://ntfy:80`, nicht über Traefik.

## Bekannte Einschränkung (Server wählt Routen nach dem Port im Host-Header)

Beim Prüfen des Stacks fiel auf: Der Server hängt Routen mit Ktor `port(...)` an, und das vergleicht den Port aus dem `Host`-Header der Anfrage, nicht den Port, auf dem die Verbindung ankommt. Folgen:

- **Öffentlicher Port hinter Traefik:** Traefik reicht `Host: kidscreentime.rusin.xyz` (ohne `:8080`) weiter, der Server antwortet überall `404`. Das Beispiel-Compose setzt deshalb eine Traefik-Middleware (`kidscreentime-host`), die den Host-Header auf `kidscreentime:8080` setzt. Mit ihr liefert `/health` über Traefik `200`. Das ist eine Umgehung. Die Dauerlösung ist eine Änderung im Server (Routen nach `call.request.local.localPort` statt nach dem Host-Header-Port); die Middleware entfällt dann.
- **Admin-Port:** Host-Port und Container-Port müssen gleich sein (`8081:8081`), sonst antwortet `/admin` mit `404` (`localhost:8790` auf `8081` ging nicht). Außerdem muss die URL den Port enthalten (`http://zotacserver:8081/admin`).

Geprüft wurde das lokal mit einem Traefik v3 und denselben Labels (ohne TLS); mit dem echten Homelab-Traefik steht es noch aus.
