# 19: Beispiel-Compose und Betrieb

**What to build:** Der Betreiber hängt Server und ntfy als Compose-Stack in das bestehende Homelab mit Traefik ein und aktualisiert mit „Version hochsetzen, pull, up“. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Homelab“, und [Ticket 11 der Map](../../screen-time/issues/11-deployment-homelab.md).

**Blocked by:** 13 Push-Resync über ntfy, 18 Release-Pipeline

**Status:** done

- [x] Das Beispiel-Compose enthält Server und ntfy, das Netz `proxy` plus ein internes Netz, Subdomains über die Traefik-`defaultRule`, Bind-Mounts unter dem Homelab-Datenpfad, `user: 1000:1000` und `restart: unless-stopped`.
- [x] Der Admin-Port ist nur als Host-Port publiziert und hat keine Traefik-Route.
- [x] Für ntfy gibt es eine Traefik-Route nur für `GET`/`HEAD`/`OPTIONS`, keine Benutzer, `cache-duration: 12h`, keine Attachments, keinen Upstream und die Standard-Rate-Limits. Der Server erreicht ntfy intern.
- [x] Eine `.env`-Vorlage enthält alle Pflichtwerte. Das Admin-Passwort steht nur in `.env`.
- [x] Eine kurze Betriebsdoku beschreibt Erstinstallation, Bootstrap über das Admin-Formular, Update-Reihenfolge (zuerst Server, dann App) und den kurzen Test des Push-Wegs nach jedem Server-Update.

## Notizen zur Umsetzung

- Dateien: `deploy/compose/` (`docker-compose.yaml` mit `include`, `server.docker-compose.yaml`, `ntfy.docker-compose.yaml`, `ntfy/server.yml`, `.env.example`), Betriebsdoku `docs/betrieb.md`, Link im README. Der Datenpfad ist über `DATA_DIR` überschreibbar (Standard `/media/ssd/docker-container-data/kidscreentime`). Image-Versionen sind fest: Server `1.0.0` (Platzhalter, auf das erste Release setzen), ntfy `v2.11.0`.
- Die Variablennamen folgen dem Server-Code (`FAMILY_TIME_ZONE`, `PUBLIC_SERVER_URL`), nicht der Map-Seite (`FAMILY_TIMEZONE`, `PUBLIC_URL`).
- Traefik: Entrypoint `websecure` und Resolver `letsencrypt` sind geraten (das Homelab-Traefik war nicht einsehbar) und stehen kommentiert in den Labels. Beide Dienste haben explizite Router (die `defaultRule` ergibt dieselben Hosts); beim ntfy-Router schränkt die Regel die Methoden auf `GET`/`HEAD`/`OPTIONS` ein. Sobald Router-Labels existieren, legt Traefik keinen Standard-Router mehr an, die Einschränkung lässt sich also nicht über die Standardroute umgehen.
- Geprüft (Docker 29, lokal gebautes Server-Image aus dem Dockerfile, `binwiederhier/ntfy:v2.11.0`): `docker compose config` gültig (ohne `ADMIN_PASSWORD` bricht es mit Hinweis ab). Stack mit Test-Override (Netz `proxy` ersetzt) gestartet: beide Container laufen als `1000:1000` auf Bind-Mounts, Server `/health` `200`, ntfy `/v1/health` healthy, ntfy intern per `POST` erreichbar, Anhänge werden abgelehnt (`attachments not allowed`), Nachricht landet in `cache.db`. Zusätzlich mit einem lokalen Traefik v3.6 (ohne TLS) und denselben Labels: ntfy `GET`/`HEAD`/`OPTIONS` `200`, `POST`/`PUT` `404`; `/admin` über die Server-Route `404`; Admin-Port direkt `200`. Danach alles abgebaut.
- **Nicht prüfbar lokal:** der echte Homelab-Traefik (TLS, Resolver, defaultRule), Push-Ende-zu-Ende mit ntfy-App und Gerät (Checkliste in Ticket 13), GHCR-Pull des Images (Release gibt es noch nicht), der Rate-Limit-Standard von ntfy.
- **Port-Routing behoben in `server/.../Application.kt` (`Route.localPort`, Test `LocalPortRoutingTest`):** Der Server hängte Routen früher mit Ktor `port(...)` an, das nach dem Port im `Host`-Header filtert (hinter Traefik überall `404`, Admin nur bei gleichem Host- und Container-Port). Jetzt wählt er nach `call.request.local.localPort`. Die Umgehungen sind entfernt: keine Host-Header-Middleware, Admin-Mapping `8790:8081`. `docker compose config` ist gültig.
