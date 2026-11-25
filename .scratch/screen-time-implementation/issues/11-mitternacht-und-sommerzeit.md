# 11: Mitternacht und Sommerzeit

**What to build:** Eine Sitzung über Mitternacht zählt ab 00:00 gegen das neue Tageskontingent. Ein laufender Alarm kann dabei verstummen. Tage mit Zeitumstellung haben 23 bzw. 25 Stunden, und alles wird in tatsächlich verstrichenen Minuten gerechnet. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Modul `domain`“.

**Blocked by:** 06 Vorwarnung und Alarm am Kindgerät

**Status:** done

- [x] `domain` teilt Sitzungen beim Lesen an Mitternacht in der Familien-Zeitzone auf und liefert die nächste Mitternacht als fälligen Zeitpunkt.
- [x] Der Alarmkern plant zu Mitternacht neu. Der Server plant Mitternacht im Prozess als Ereignis.
- [x] `domain`-Tests decken ab: Sitzung über Mitternacht, 23- und 25-Stunden-Tage, Sitzung über die Zeitumstellung, Alarm verstummt um 00:00 bei neuem Kontingent.
- [x] Ein Robolectric-Test prüft die Neuplanung des Alarmkerns zu Mitternacht.
- [x] Die Familien-Zeitzone wirkt unabhängig von der Zeitzone von Gerät und Container.

## Notizen zur Umsetzung

**`domain`:** `Ausloeser.MITTERNACHT` in `faelligeZeitpunkte`: der Zeitpunkt ist `Tagesstand.tagesende` (nächste Mitternacht in der Familien-Zeitzone, `atStartOfDay`, daher 23 bzw. 25 Stunden an Umstellungstagen). Er steht nur an, solange die Sitzung über Mitternacht hinaus läuft (der Automatische Stopp läge hinter dem Tagesende); ohne Sitzung oder bei einem Stopp davor gibt es ihn nicht. Das Aufteilen der Sitzungen an Mitternacht und das Rechnen in verstrichenen Minuten leistete `tagesstand` schon (Ticket 02), Ticket 11 sichert es per `MitternachtUndSommerzeitTest` ab (Sitzung über Mitternacht, 23-/25-h-Tag, Sitzung über beide Umstellungen, Alarm verstummt um 00:00, Stopp mit dem Kontingent des neuen Tages, abweichende JVM-Zeitzone per `TimeZone.setDefault`). Der Test in `FaelligeZeitpunkteTest` zu „Nach Mitternacht“ erwartet nun `MITTERNACHT` statt einer leeren Liste.

**Alarmkern:** `Ausloeser.MITTERNACHT -> Unit` in `AlarmKern.trigger`; danach plant `planLocked` mit dem neuen Tag neu, ein klingender Alarm verstummt, wenn das neue Kontingent Restzeit lässt. Die Vorwarnung kommt weiterhin höchstens einmal je Sitzung (auch über Mitternacht). (Überholt durch den Nachtrag in Ticket 06: das neue Kontingent nach Mitternacht bekommt seine eigene Vorwarnung.) `MitternachtAlarmTest` (Robolectric, echter Server): Verstummen um 00:00, Mitternacht als nächster geplanter Zeitpunkt, Zeitzone des Geräts egal (JVM-Default Kiritimati), und das Tablet rechnet offline über Mitternacht mit `templateTomorrow` aus dem Snapshot weiter (eigene Vorlagenversion ab Dienstag mit 30 min). Das Weiterrechnen funktionierte schon, es war nur nicht abgesichert; der Snapshot enthält die Versionen für heute und morgen, nach Mitternacht ist „morgen“ der neue Tag. Zwei Tage offline rechnen weiter mit der Version von „morgen“ (bis zum nächsten Sync).

**Server:** `MidnightEvents` (`TimedEventSource`, `ChildService.nextMidnight`) steht in der Quellenliste von `ServerServices.create`. `nextDue` ist `MITTERNACHT` aus `domain`, `runDue` hat nichts zu tun: alle Werte werden aus der Datenbank für die angefragte Zeit gerechnet, die Quelle sorgt nur dafür, dass der Planer genau zu Mitternacht aufwacht und neu rechnet (Automatischer Stopp sieht den neuen Tag, vorher wäre es bis zu 1 min später gewesen). Ohne laufende Sitzung hat der Server keinen Mitternachtszeitpunkt (der bestehende Test „Ohne Sitzung steht nichts an“ gilt weiter). Ticket 14 (Benachrichtigung „Restzeit 0“) braucht eine eigene Quelle mit `nextDue` aus `domain`; sie wird zu Mitternacht automatisch neu bewertet. `MidnightApiTest`: Planer wacht um Mitternacht auf, Stopp mit Kontingent des neuen Tages, Umstellungstage, abweichende JVM-Zeitzone.

**Zeitzone:** Produktivcode nutzt nur die Familien-Zeitzone aus Konfiguration beziehungsweise Snapshot (`ZoneId.of(timeZone)`); keine Stelle liest die Standardzeitzone (Architekturtest verbietet `ZoneId.systemDefault`).
