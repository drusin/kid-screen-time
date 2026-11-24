# 03: Erstes Elterngerät koppeln

**What to build:** Der Bootstrap. Der Betreiber startet den Server, öffnet das Admin-Formular im Heimnetz, gibt das Admin-Passwort ein und erhält einen Kopplungscode für ein Elterngerät. Vor der Kopplung zeigt die App nur „Gerät koppeln“. Dort gibt man Server-URL und Code ein, die App wird zum Elterngerät und zeigt eine (noch leere) Eltern-Startseite. Derselbe Weg dient später der Recovery. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Server“ (Persistenz, Sicherheit, Admin-Webformular, Konfiguration) und „Modul `api`“ (Kopplung, Eltern-Snapshot).

**Blocked by:** 01 Gerüst und CI-Gate

**Status:** done

- [x] Admin-Passwort, Familien-Zeitzone, öffentliche Server-URL sowie öffentliche und interne ntfy-URL kommen aus Umgebungsvariablen. Fehlt ein Pflichtwert, startet der Server nicht.
- [x] SQLite-Datei mit Migrationen beim Start. Tests laufen gegen eine echte SQLite-Datei im Temp-Ordner.
- [x] Das Admin-Formular (kotlinx.html, ohne JS) ist nur auf dem Admin-Port erreichbar, verlangt das Passwort und kann nur Kopplungscodes für Elterngeräte erzeugen.
- [x] Kopplungscodes sind einmalig, verfallen nach 10 Minuten und werden nur als Hash gespeichert.
- [x] Ein globales Rate-Limit (z. B. 10 Fehlversuche pro Minute) gilt für das Einlösen von Codes und für das Admin-Passwort und antwortet mit `429`.
- [x] Beim Einlösen entsteht ein zufälliges 256-Bit-Geräte-Token. Am Server liegt nur sein Hash, am Gerät liegt es im Android Keystore.
- [x] Der Eltern-Snapshot liefert Serverzeit, Geräteliste und eine leere Kinderliste. Ein Kindgerät-Token bekommt dort `403`.
- [x] Die App zeigt nach der Kopplung die Eltern-Startseite. Dort steht der Hinweis, dass noch kein Kind gekoppelt ist.

## Notizen zur Umsetzung

- **DB:** plain JDBC mit `org.xerial:sqlite-jdbc` und einem eigenen Mini-Migrationsrunner (`Database`, Version in `PRAGMA user_version`, Migrationen als `db/migration/NNN_*.sql` plus Eintrag in `Database.MIGRATIONS`). Grund: kleines Schema, kein ORM nötig, kein Flyway (zusätzliche Abhängigkeiten, Service-Dateien im Fat-JAR). Eine Verbindung mit Lock, WAL, Fremdschlüssel an. Spätere Tickets hängen neue Migrationen an und ändern alte nie.
- **Schema (001):** `children(id, name, created_at)`, `devices(id, name, role, child_id, token_hash, paired_at, last_seen_at, push_*, unpaired_at)` mit eindeutigem Index „höchstens ein aktives Kindgerät pro Kind“, `pairing_codes(id, code_hash, role, child_id, new_child_name, created_at, expires_at, redeemed_at)`. Weitere Spalten (Vorwarnschwelle usw.) kommen mit ihren Tickets.
- **Endpunkte:** `POST /api/v1/pairing/redeem` (ohne Token; `400` ungültig/abgelaufen/benutzt, ununterscheidbar; `429` Rate-Limit), `GET /api/v1/parent/snapshot` (Bearer, nur Elterngerät: `401` ohne/ungültig/entkoppelt, `403` Kindgerät), `GET /health`. Admin-Port: `GET /admin`, `POST /admin/parent-code` (Formular mit Passwort bei jedem Absenden, keine Session). Public- und Admin-Routen sind per lokalem Port getrennt (`port(...)`), auf dem jeweils anderen Port gibt es `404`.
- **Kopplungscode:** 8 Zeichen aus einem Alphabet ohne 0/O/1/I/L, angezeigt als `ABCD-EFGH`, beim Einlösen tolerant gegen Klein-/Großschreibung und Bindestrich. Nur SHA-256-Hash in der DB. Gültig bis `now < expires_at` (genau nach 10 min verfallen).
- **Rate-Limit:** `FailureRateLimiter`, ein globaler Zähler für Fehlversuche (Code und Admin-Passwort gemeinsam), 10 Fehlversuche pro Minute auf der monotonen Uhr. Gesperrt wird jeder Versuch, auch ein richtiger, bis der älteste Fehlversuch älter als 1 min ist.
- **Konfiguration:** Pflicht `ADMIN_PASSWORD`, `FAMILY_TIME_ZONE`, `PUBLIC_SERVER_URL`, `NTFY_PUBLIC_URL`, `NTFY_INTERNAL_URL`; optional `DATABASE_PATH` (Standard `data/kid-screen-time.db`), `PORT` (8080), `ADMIN_PORT` (8081). Fehlt etwas, nennt der Server alles Fehlende und beendet sich mit Exit-Code 1. Ntfy-URLs und Zeitzone werden validiert, aber erst von späteren Tickets benutzt.
- **Token:** 256 Bit zufällig, URL-safe Base64, Bearer-Header; Server speichert SHA-256. `zuletzt gesehen` wird bei jedem authentifizierten Aufruf gesetzt.
- **Redeem erzeugt Kinder:** Ein Code mit `new_child_name` legt das Kind beim Einlösen an. Erzeugen solcher Codes (Endpunkt der Eltern) und das Ersetzen eines alten Kindgeräts gehören zu Ticket 04/Eltern-Aktionen; der Unique-Index würde ein zweites aktives Kindgerät bisher mit einem Fehler abweisen. Die Tests erzeugen Kindgerät-Codes direkt über `PairingService.createCode`.
- **App:** manuelle DI (`AppContainer`), Ktor-Client mit OkHttp, `TokenStore`-Schnittstelle mit `KeystoreTokenStore` (AES-GCM-Schlüssel im Android Keystore, Chiffretext in privaten SharedPreferences), Rolle und Server-URL in `PairingStorage`. Der Root-Bildschirm folgt `DeviceState` (ungekoppelt / gekoppelt mit Rolle), kein NavHost. Server-URL ohne Schema bekommt `https://`; Klartext-HTTP bleibt durch Android-Standard blockiert (Produktiv-URL ist HTTPS).
- **Nicht gebaut:** QR-Code-Scan (nur Eintippen, wie das Ticket verlangt), Kindgerät-Bildschirme (Platzhaltertext), Behandlung von `401` in der App (Ticket 12), Eltern-Snapshot-Cache (Ticket 17). Bei `401` oder fehlendem Netz zeigt die Startseite nur „Server nicht erreichbar“.
- **Sandbox:** Umlaute in Testnamen brauchen `LC_ALL=C.UTF-8` für Gradle- und Kotlin-Daemon, sonst scheitert die Kompilierung an Dateinamen. Die Sandbox setzt es jetzt in `/etc/sandbox-persistent.sh`; die CI-Runner haben UTF-8.
