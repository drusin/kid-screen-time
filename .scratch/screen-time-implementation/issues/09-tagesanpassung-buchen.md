# 09: Tagesanpassung buchen

**What to build:** Auf der Eltern-Startseite gibt es „Heute anpassen“. Mit einem Stepper (±5 min) bereitet der Elternteil eine Bonuszeit oder einen Abzug vor, sieht eine Vorschau des neuen Tageskontingents, bucht und kann die letzte Anpassung zurücknehmen. Am Tablet wirkt sie nach dem nächsten Sync sofort, auch während einer laufenden Sitzung. Bis Ticket 13 umgesetzt ist, kommt der Sync durch App-Start oder Entsperren. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Eltern: Status und Tagesanpassung“ und „Elterngerät – Aktionen“.

**Blocked by:** 06 Vorwarnung und Alarm am Kindgerät

**Status:** done

- [x] Tagesanpassungen werden nur angehängt (Kind, Tag, Delta, Buchungszeitpunkt, buchendes Gerät) und gelten nur für heute.
- [x] „Letzte zurücknehmen“ bucht eine Gegenbuchung.
- [x] Die Aktion liefert den neuen Eltern-Snapshot. Danach gleicht das Elterngerät ab.
- [x] Die Vorschau zeigt das neue Tageskontingent, nie unter 0.
- [x] Am Tablet rechnet der Alarmkern nach dem Sync neu. Steigt die Restzeit über 0, verstummt ein laufender Alarm. Fällt sie durch einen Abzug unter den Verbrauch, klingelt der Alarm sofort.
- [x] Ein ViewModel-Test (Robolectric) deckt Stepper und Vorschau ab.
- [x] Ein Sync-Szenario-Test prüft: Eine Tagesanpassung während einer Offline-Phase führt nach dem Sync zu sofortigem Alarm.
- [x] Ein Server-API-Test prüft Buchen, Gegenbuchung und die Rollenprüfung.

## Notizen zur Umsetzung

- **Endpunkte:** `POST ApiPaths.PARENT_DAILY_ADJUSTMENTS` (`DailyAdjustmentRequest(childId, deltaMinutes)`) und `POST ApiPaths.PARENT_DAILY_ADJUSTMENTS_UNDO` (`UndoDailyAdjustmentRequest(childId)`), nur Elterngerät; Antwort ist der neue `ParentSnapshot`. `400` bei Delta 0, |Delta| > 24 h oder unbekanntem Kind, `409`, wenn nichts zurückzunehmen ist. Der Stepper ist reine App-Sache (±5 min), der Server akzeptiert jedes Delta ungleich 0 bis 24 h (Tippfehlerschutz, keine Domänenregel).
- **Schema:** Migration 003 ergänzt `daily_adjustments.reverses_id`. Die Spalte für das buchende Gerät (`device_id`) gab es schon. Gegenbuchung = neue Zeile mit umgekehrtem Delta, die auf das Original zeigt. „Letzte zurücknehmen“ trifft die jüngste heutige Buchung, die weder Gegenbuchung noch schon zurückgenommen ist; zweimal zurücknehmen nimmt also die vorletzte zurück statt die Gegenbuchung rückgängig zu machen. `ParentChildSnapshot.lastAdjustmentMinutes` (optional) sagt der App, was zurückgenommen würde (Knopf „Letzte zurücknehmen (+30 min)“, ausgeblendet ohne).
- **Folgen am Server:** `notifyStandChanged()` (Planer rechnet neu, getestet: der Automatische Stopp verschiebt sich) und nach der Antwort `push.signalChild(childId)` (Server-API-Test mit `FakeNtfy`: nur das Kindgerät bekommt das Signal, eine abgelehnte Anfrage keins). Ticket 13: Dieser Weg ist verdrahtet; das dortige Sammel-Kriterium bleibt offen, solange 10 und 12 fehlen.
- **Kindgerät:** Der Alarmkern leistet das Verlangte schon über `observe` (Ticket 06). Neu abgesichert durch `DailyAdjustmentScenarioTest` mit echtem `ParentRepository` gegen den echten Server, ohne direkten `replan`-Aufruf: Abzug in der Offline-Phase gibt nach dem Sync sofort Alarm, Bonuszeit lässt einen laufenden Alarm verstummen, Zurücknehmen lässt ihn wieder klingeln. Am Kindgerät war keine Änderung nötig.
- **Eltern-App:** `ParentHomeViewModel` hat Stepper (`stepAdjustment`), Vorschau (`previewAllowanceMinutes` = Vorlage + bisherige Anpassungen + Vorbereitetes, nie unter 0), `bookAdjustment`, `undoLastAdjustment`. Das Aktionsmuster (`ParentRepository.action`, `ParentActionResult`, `runAction`) steht in `AGENTS.md`; Ticket 17 kann Aktionen darüber offline deaktivieren.
- **Manuell zu prüfen:** Aussehen der Karte „Heute anpassen“ am Gerät (Compose ist nicht per UI-Test abgedeckt).
