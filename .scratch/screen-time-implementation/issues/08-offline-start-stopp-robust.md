# 08: Offline-Start und -Stopp robust

**What to build:** Ohne WLAN startet und stoppt das Kind genauso. Das Tablet zeigt „gerechnet mit dem Stand von …“. Sobald wieder Netz da ist, landen die Sitzungen korrekt am Server, und die Eltern sehen sie als „offline erfasst“. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Sync-Protokoll“.

**Blocked by:** 05 Sitzung starten und stoppen

**Status:** ready-for-agent

- [ ] Scheitert ein Sync, wiederholt ihn ein WorkManager-Job mit Netzwerk-Bedingung. Es gibt keinen separaten Online-Pfad und keinen periodischen Job.
- [ ] Der Batch enthält alle offenen Sitzungen in Startreihenfolge.
- [ ] Erfährt der Server einen Start erst mit Verzögerung, markiert er die Sitzung als „offline erfasst“.
- [ ] Liegt ein Neustart zwischen Zeitpunkt und Sync, schickt das Gerät seine Wanduhrzeit, korrigiert um den Versatz aus dem letzten Snapshot.
- [ ] Der Server begrenzt jeden Zeitpunkt auf [Ende der vorigen Sitzung, jetzt]. Zukünftige Zeitpunkte und Überlappungen werden gekappt.
- [ ] Doppelte Batches sind wirkungslos.
- [ ] Offline zeigt das Kindgerät den Hinweis „gerechnet mit dem Stand von …“.
- [ ] Gesynct wird auch beim Entsperren des Geräts.
- [ ] Sync-Szenario-Tests decken ab: Offline-Start mit späterem Sync, doppelte Batches, Begrenzung, Neustart zwischen Zeitpunkt und Sync.
