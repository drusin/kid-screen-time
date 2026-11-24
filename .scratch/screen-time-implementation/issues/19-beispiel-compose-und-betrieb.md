# 19: Beispiel-Compose und Betrieb

**What to build:** Der Betreiber hängt Server und ntfy als Compose-Stack in das bestehende Homelab mit Traefik ein und aktualisiert mit „Version hochsetzen, pull, up“. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Homelab“, und [Ticket 11 der Map](../../screen-time/issues/11-deployment-homelab.md).

**Blocked by:** 13 Push-Resync über ntfy, 18 Release-Pipeline

**Status:** ready-for-agent

- [ ] Das Beispiel-Compose enthält Server und ntfy, das Netz `proxy` plus ein internes Netz, Subdomains über die Traefik-`defaultRule`, Bind-Mounts unter dem Homelab-Datenpfad, `user: 1000:1000` und `restart: unless-stopped`.
- [ ] Der Admin-Port ist nur als Host-Port publiziert und hat keine Traefik-Route.
- [ ] Für ntfy gibt es eine Traefik-Route nur für `GET`/`HEAD`/`OPTIONS`, keine Benutzer, `cache-duration: 12h`, keine Attachments, keinen Upstream und die Standard-Rate-Limits. Der Server erreicht ntfy intern.
- [ ] Eine `.env`-Vorlage enthält alle Pflichtwerte. Das Admin-Passwort steht nur in `.env`.
- [ ] Eine kurze Betriebsdoku beschreibt Erstinstallation, Bootstrap über das Admin-Formular, Update-Reihenfolge (zuerst Server, dann App) und den kurzen Test des Push-Wegs nach jedem Server-Update.
