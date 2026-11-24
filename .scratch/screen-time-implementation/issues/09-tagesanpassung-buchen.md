# 09: Tagesanpassung buchen

**What to build:** Auf der Eltern-Startseite gibt es „Heute anpassen“. Mit einem Stepper (±5 min) bereitet der Elternteil eine Bonuszeit oder einen Abzug vor, sieht eine Vorschau des neuen Tageskontingents, bucht und kann die letzte Anpassung zurücknehmen. Am Tablet wirkt sie nach dem nächsten Sync sofort, auch während einer laufenden Sitzung. Bis Ticket 13 umgesetzt ist, kommt der Sync durch App-Start oder Entsperren. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Eltern: Status und Tagesanpassung“ und „Elterngerät – Aktionen“.

**Blocked by:** 06 Vorwarnung und Alarm am Kindgerät

**Status:** ready-for-agent

- [ ] Tagesanpassungen werden nur angehängt (Kind, Tag, Delta, Buchungszeitpunkt, buchendes Gerät) und gelten nur für heute.
- [ ] „Letzte zurücknehmen“ bucht eine Gegenbuchung.
- [ ] Die Aktion liefert den neuen Eltern-Snapshot. Danach gleicht das Elterngerät ab.
- [ ] Die Vorschau zeigt das neue Tageskontingent, nie unter 0.
- [ ] Am Tablet rechnet der Alarmkern nach dem Sync neu. Steigt die Restzeit über 0, verstummt ein laufender Alarm. Fällt sie durch einen Abzug unter den Verbrauch, klingelt der Alarm sofort.
- [ ] Ein ViewModel-Test (Robolectric) deckt Stepper und Vorschau ab.
- [ ] Ein Sync-Szenario-Test prüft: Eine Tagesanpassung während einer Offline-Phase führt nach dem Sync zu sofortigem Alarm.
- [ ] Ein Server-API-Test prüft Buchen, Gegenbuchung und die Rollenprüfung.
