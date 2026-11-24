# 07: Automatischer Stopp

**What to build:** Läuft eine Sitzung bis zur Überziehungsgrenze weiter, wird sie automatisch gestoppt: am Server, auch wenn das Tablet offline ist, und am Tablet, auch wenn es offline ist. Der Alarm hört dann auf. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Zeitgesteuerte Ereignisse im Prozess“ und „Automatischer Stopp am Server“.

**Blocked by:** 06 Vorwarnung und Alarm am Kindgerät

**Status:** done

- [x] `domain` liefert den Zeitpunkt des Automatischen Stopps mit dem Endzeitpunkt der Sitzung.
- [x] Der Server berechnet pro Kind den nächsten fälligen Zeitpunkt und plant ihn im Prozess, ohne Job-Queue und ohne gespeicherte Planungen. Bei jeder Änderung und nach einem Neustart rechnet er aus der DB neu.
- [x] Der Server setzt das Ende auf den Zeitpunkt, an dem die Grenze erreicht wurde, mit dem Grund „Automatischer Stopp“.
- [x] Das Tablet stoppt lokal zum selben Zeitpunkt über die Outbox. Weil das frühere Ende gewinnt, entsteht kein Konflikt.
- [x] Ein Server-API-Test stellt die Uhr vor und prüft, dass Ende und Grund in der DB stehen, auch nach einem simulierten Neustart.
- [x] Ein Sync-Szenario-Test prüft, dass ein späterer Stopp des Kindes nach dem Automatischen Stopp nichts ändert.
- [x] Ein Robolectric-Test prüft, dass der Alarmkern den lokalen Automatischen Stopp plant.

## Notizen zur Umsetzung

**`domain`:** `Ausloeser.AUTOMATISCHER_STOPP` in `faelligeZeitpunkte`. Zeitpunkt = `jetzt + Restzeit − Überziehung + Überziehungsgrenze`, mindestens der Start der Sitzung (lag die Überziehung schon vor dem Start über der Grenze, endet die Sitzung mit ihrem Start). Ist er schon vorbei, steht er mit seinem tatsächlichen Zeitpunkt an, nicht mit `jetzt`, damit das Ende stimmt, auch wenn Alarm oder Server spät dran sind. Alles, was nicht vor dem Stopp liegt (auch die Alarmwiederholung), entfällt. `FaelligeZeitpunkte.automatischerStopp` ist der Kurzzugriff. Weiter gilt: nach Tagesende gibt es keine Zeitpunkte (Ticket 11).

**Server:** `EventScheduler` + `TimedEventSource` (`EventScheduler.kt`), `AutomaticStopEvents` (`AutomaticStop.kt`), `ChildService.nextAutomaticStop/stopAutomatically/childIds/onStandChanged`. Keine Schema-Änderung: `sessions.end_reason` kennt `AUTOMATIC_STOP` schon seit Migration 002, es gibt keine neue Migration (Ticket 13 nimmt die nächste Nummer, 003). Die Schleife läuft als Coroutine der Application (`serverModule(..., runScheduler = true)`), schläft höchstens 1 min (deckt auch Sprünge der Wanduhr) und wird von `ChildService.sync` geweckt. Bei Fehlern probiert sie nach 1 s neu. Der Haken für Ticket 13/14 ist `services.automaticStops.onStopped { }`.

**Tablet:** `AlarmKern.trigger` behandelt `AUTOMATISCHER_STOPP` über `ChildRepository.stopSession(id, at)` (neuer optionaler Endzeitpunkt, monotone Zeit entsprechend zurückgerechnet), plant aus dem neuen Stand neu (dadurch endet ein Klingeln) und synchronisiert danach außerhalb des Locks. Die Testsitzung des Debug-Builds wird dabei beendet.

**Entscheidungen:**
- Das Tablet markiert seinen lokalen Stopp nicht als „Automatisch gestoppt“ (die Outbox kennt nur das Ende). Kommt der Sync des Tablets vor dem Lauf des Servers an, steht die Sitzung als `STOPPED` mit gleichem Ende da. Im Normalfall (Server und Tablet feuern zur selben Zeit, der Sync folgt danach) hat der Server die Sitzung schon mit dem Grund gesetzt. Wer die Markierung im Verlauf (Ticket 16) lückenlos braucht, ergänzt einen optionalen Endegrund im Sync-Protokoll.
- Das frühere Ende gewinnt wie bisher nur so: ein Stopp für eine schon beendete Sitzung ändert nichts (Spezifikation, `ChildService.upsert`). Stoppt das Kind offline vor der Grenze und der Server stoppt automatisch später, weil er nichts wusste, bleibt das Ende des Servers. Das ist die spezifizierte Regel, Ticket 08 kann sie bei Bedarf schärfen.
- Tests: `FaelligeZeitpunkteTest` (domain), `AutomaticStopApiTest` (Server, Uhr vorstellen, Neustart als zweites `TestServer`, Listener, echte Schleife), `AutomaticStopScenarioTest` (Sync-Szenario), `AlarmKernTest` (Robolectric, geplanter Zeitpunkt und lokaler Stopp). Manuell: `docs/alarm-checkliste.md` Punkt 10.
