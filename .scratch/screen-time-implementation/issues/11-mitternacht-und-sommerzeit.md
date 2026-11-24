# 11: Mitternacht und Sommerzeit

**What to build:** Eine Sitzung über Mitternacht zählt ab 00:00 gegen das neue Tageskontingent. Ein laufender Alarm kann dabei verstummen. Tage mit Zeitumstellung haben 23 bzw. 25 Stunden, und alles wird in tatsächlich verstrichenen Minuten gerechnet. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Modul `domain`“.

**Blocked by:** 06 Vorwarnung und Alarm am Kindgerät

**Status:** ready-for-agent

- [ ] `domain` teilt Sitzungen beim Lesen an Mitternacht in der Familien-Zeitzone auf und liefert die nächste Mitternacht als fälligen Zeitpunkt.
- [ ] Der Alarmkern plant zu Mitternacht neu. Der Server plant Mitternacht im Prozess als Ereignis.
- [ ] `domain`-Tests decken ab: Sitzung über Mitternacht, 23- und 25-Stunden-Tage, Sitzung über die Zeitumstellung, Alarm verstummt um 00:00 bei neuem Kontingent.
- [ ] Ein Robolectric-Test prüft die Neuplanung des Alarmkerns zu Mitternacht.
- [ ] Die Familien-Zeitzone wirkt unabhängig von der Zeitzone von Gerät und Container.
