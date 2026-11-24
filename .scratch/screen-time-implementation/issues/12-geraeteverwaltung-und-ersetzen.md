# 12: Geräteverwaltung und Ersetzen

**What to build:** Im ⋮-Menü „Einstellungen & Geräte“ sieht der Elternteil alle Geräte (Name, Rolle, Kopplungsdatum, zuletzt gesehen), kann jedes entkoppeln (auch das eigene, aber nicht das letzte Elterngerät) und ein weiteres Elterngerät koppeln. Ein entkoppeltes Gerät zeigt „Gerät wurde entkoppelt“. Ein neues Kindgerät ersetzt das alte, und eine laufende Sitzung läuft am neuen Gerät weiter. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Kopplung und Geräte“ und „Sync-Protokoll“ (Ersetztes Kindgerät).

**Blocked by:** 05 Sitzung starten und stoppen

**Status:** ready-for-agent

- [ ] Die Geräteliste ist Teil des Eltern-Snapshots. „Zuletzt gesehen“ entsteht aus den Syncs.
- [ ] Das letzte Elterngerät kann nicht entkoppelt werden.
- [ ] Nach dem Entkoppeln bekommt das Gerät `401` und zeigt „Gerät wurde entkoppelt“.
- [ ] Am Elterngerät lässt sich ein Kopplungscode für ein weiteres Elterngerät erzeugen (QR, Text, Countdown).
- [ ] Das Koppeln eines neuen Kindgeräts entkoppelt das alte automatisch. Pro Kind gibt es höchstens ein aktives Kindgerät.
- [ ] Das alte Kindgerät verwirft bei `401` seine Outbox. Das neue übernimmt die laufende Sitzung über die Sitzungs-UUID im Snapshot.
- [ ] Server-API-Tests decken ab: letztes Elterngerät, `401` nach Entkoppeln, Ersetzen des Kindgeräts.
- [ ] Sync-Szenario-Tests prüfen: Ersetztes Kindgerät verwirft die Outbox, das neue Kindgerät übernimmt die Sitzung.
