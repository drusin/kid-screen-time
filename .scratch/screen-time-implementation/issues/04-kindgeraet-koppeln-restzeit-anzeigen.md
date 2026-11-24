# 04: Kindgerät koppeln und Restzeit anzeigen

**What to build:** Am Elterngerät erzeugt der Elternteil einen Kopplungscode für die Rolle Kindgerät. Gibt es noch kein Kind, gibt er zuerst den Namen ein. Der Code erscheint als QR-Code (mit Server-URL) und als Text zum Abtippen, mit Countdown. Das Tablet scannt den QR-Code oder tippt den Code ein, wird zum Kindgerät und zeigt den Ring (Variante A) mit Restzeit, Zustandswort, Tageskontingent mit Aufschlüsselung und heute genutzter Zeit. Die Eltern-Statuskarte zeigt denselben Stand. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Kopplung und Geräte“, „Kind-Snapshot“, „UI Kindgerät“ und „UI Elterngerät“.

**Blocked by:** 02 `domain`: Tageskontingent und Restzeit, 03 Erstes Elterngerät koppeln

**Status:** ready-for-agent

- [ ] Beim Koppeln wählt man zuerst die Rolle. Bei Kindgerät wird ein neues Kind angelegt, wenn noch keins existiert. Der Ersetzen-Hinweis erscheint, wenn das Kind schon ein Kindgerät hat.
- [ ] Ein neues Kind bekommt eine Wochenvorlage mit sinnvollen Standardwerten, eine Vorwarnschwelle von 5 min und eine Überziehungsgrenze von 60 min.
- [ ] Der Kind-Snapshot enthält alles aus der Spezifikation, auch die gültige Wochenvorlage für heute und morgen, und ist immer vollständig.
- [ ] Der Server berechnet die Werte mit `domain`.
- [ ] Das Kindgerät speichert den Snapshot in Room und rechnet lokal mit `domain`.
- [ ] Am Kindgerät sind keine Einstellungen sichtbar. Ein Kindgerät-Token bekommt bei Eltern-Endpunkten `403`.
- [ ] Die Statuskarte am Elterngerät zeigt Restzeit, Fortschrittsbalken und Aufschlüsselung.
- [ ] Ein Server-API-Test deckt Kopplung, Rollenprüfung und beide Snapshots ab.
