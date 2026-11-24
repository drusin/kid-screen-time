# 07: Automatischer Stopp

**What to build:** Läuft eine Sitzung bis zur Überziehungsgrenze weiter, wird sie automatisch gestoppt: am Server, auch wenn das Tablet offline ist, und am Tablet, auch wenn es offline ist. Der Alarm hört dann auf. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Zeitgesteuerte Ereignisse im Prozess“ und „Automatischer Stopp am Server“.

**Blocked by:** 06 Vorwarnung und Alarm am Kindgerät

**Status:** ready-for-agent

- [ ] `domain` liefert den Zeitpunkt des Automatischen Stopps mit dem Endzeitpunkt der Sitzung.
- [ ] Der Server berechnet pro Kind den nächsten fälligen Zeitpunkt und plant ihn im Prozess, ohne Job-Queue und ohne gespeicherte Planungen. Bei jeder Änderung und nach einem Neustart rechnet er aus der DB neu.
- [ ] Der Server setzt das Ende auf den Zeitpunkt, an dem die Grenze erreicht wurde, mit dem Grund „Automatischer Stopp“.
- [ ] Das Tablet stoppt lokal zum selben Zeitpunkt über die Outbox. Weil das frühere Ende gewinnt, entsteht kein Konflikt.
- [ ] Ein Server-API-Test stellt die Uhr vor und prüft, dass Ende und Grund in der DB stehen, auch nach einem simulierten Neustart.
- [ ] Ein Sync-Szenario-Test prüft, dass ein späterer Stopp des Kindes nach dem Automatischen Stopp nichts ändert.
- [ ] Ein Robolectric-Test prüft, dass der Alarmkern den lokalen Automatischen Stopp plant.
