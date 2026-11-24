# 14: Eltern-Benachrichtigungen

**What to build:** Alle Elterngeräte bekommen eine Heads-up-Benachrichtigung, wenn die Restzeit einer laufenden Sitzung 0 erreicht („Mia: Kontingent abgelaufen (seit 14:32)“), auch wenn das Tablet offline ist. Beim Automatischen Stopp kommt „Mia: Automatischer Stopp nach 60 min Überziehung“. Jede Benachrichtigung kommt einmal, nie doppelt und nie nachträglich. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Eltern-Benachrichtigungen“.

**Blocked by:** 07 Automatischer Stopp, 13 Push-Resync über ntfy

**Status:** ready-for-agent

- [ ] Der Server plant „Restzeit 0“ pro Kind im Prozess und benachrichtigt alle Elterngeräte, mit dem Kindnamen vorne.
- [ ] Die Payload enthält den anzuzeigenden Text, sodass die Benachrichtigung ohne eigenen Netzzugriff der App erscheint.
- [ ] Erfährt der Server eine laufende Sitzung erst beim Sync und ist sie schon in der Überziehung, kommt die Alarm-Benachrichtigung jetzt, genau einmal.
- [ ] Für Sitzungen, die offline schon beendet wurden, kommen keine Benachrichtigungen.
- [ ] Nach einem Neustart des Servers kommt keine Benachrichtigung doppelt. Der Mechanismus (abgeleitet oder „gemeldet“-Vermerk pro Sitzung) ist im Ticket festgehalten.
- [ ] Server-API-Tests stellen die Uhr vor und prüfen jeweils genau eine Benachrichtigung bei Alarm, Automatischem Stopp, nachträglich bekannter Sitzung und nach einem Neustart.
