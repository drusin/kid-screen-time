# 03: Erstes Elterngerät koppeln

**What to build:** Der Bootstrap. Der Betreiber startet den Server, öffnet das Admin-Formular im Heimnetz, gibt das Admin-Passwort ein und erhält einen Kopplungscode für ein Elterngerät. Vor der Kopplung zeigt die App nur „Gerät koppeln“. Dort gibt man Server-URL und Code ein, die App wird zum Elterngerät und zeigt eine (noch leere) Eltern-Startseite. Derselbe Weg dient später der Recovery. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Server“ (Persistenz, Sicherheit, Admin-Webformular, Konfiguration) und „Modul `api`“ (Kopplung, Eltern-Snapshot).

**Blocked by:** 01 Gerüst und CI-Gate

**Status:** ready-for-agent

- [ ] Admin-Passwort, Familien-Zeitzone, öffentliche Server-URL sowie öffentliche und interne ntfy-URL kommen aus Umgebungsvariablen. Fehlt ein Pflichtwert, startet der Server nicht.
- [ ] SQLite-Datei mit Migrationen beim Start. Tests laufen gegen eine echte SQLite-Datei im Temp-Ordner.
- [ ] Das Admin-Formular (kotlinx.html, ohne JS) ist nur auf dem Admin-Port erreichbar, verlangt das Passwort und kann nur Kopplungscodes für Elterngeräte erzeugen.
- [ ] Kopplungscodes sind einmalig, verfallen nach 10 Minuten und werden nur als Hash gespeichert.
- [ ] Ein globales Rate-Limit (z. B. 10 Fehlversuche pro Minute) gilt für das Einlösen von Codes und für das Admin-Passwort und antwortet mit `429`.
- [ ] Beim Einlösen entsteht ein zufälliges 256-Bit-Geräte-Token. Am Server liegt nur sein Hash, am Gerät liegt es im Android Keystore.
- [ ] Der Eltern-Snapshot liefert Serverzeit, Geräteliste und eine leere Kinderliste. Ein Kindgerät-Token bekommt dort `403`.
- [ ] Die App zeigt nach der Kopplung die Eltern-Startseite. Dort steht der Hinweis, dass noch kein Kind gekoppelt ist.
