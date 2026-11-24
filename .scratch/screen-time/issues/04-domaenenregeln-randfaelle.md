# Wie verhalten sich Kontingent und Sitzung in den Randfällen?

Type: grilling
Status: resolved
Assignee: Dawid Rusin
Blocked by: -

## Question

Die fachlichen Regeln für Randfälle festlegen:
- Ist eine Tagesausnahme ein **absoluter** Wert („heute 90 min“) oder ein **Delta** („+30 min Bonus“), oder beides?
- Die Eltern senken das Kontingent unter den bisherigen Verbrauch, während eine Sitzung läuft: Gibt es sofort einen Alarm?
- Die Eltern ändern die Wochenvorlage: Gilt das ab heute oder ab morgen?
- Vergessenes Stoppen: Gibt es einen automatischen Stopp nach X Stunden oder um Mitternacht? Können Eltern Sitzungen nachträglich korrigieren oder löschen?
- Was passiert, wenn das Tablet beim Start oder Stopp offline ist?
- Darf das Kind eine Sitzung starten, wenn die Restzeit schon 0 ist (dann klingelt es sofort)?
- Welche Zeitzone gilt, und was ist mit Sommerzeit?

## Answer

Im Grilling mit dem Nutzer am 2026-11-24 entschieden. Die Begriffe stehen in [GLOSSARY.md](../../../GLOSSARY.md).

1. **Tagesanpassung statt Tagesausnahme:** Die Eltern geben einen Zu- oder Abschlag (±) in Minuten. Er gilt **nur für den heutigen Tag** und wirkt sofort. Mehrere Tagesanpassungen eines Tages addieren sich, zurückgenommen wird per Gegenbuchung. Für künftige Tage kann man keine Anpassungen vorab eintragen.
2. **Tageskontingent** = Wert der Wochenvorlage, wie er um 00:00 galt, plus die Summe der heutigen Tagesanpassungen, mindestens 0.
3. **Wochenvorlage:** Eine Änderung wird sofort gespeichert, gilt aber erst **ab dem nächsten Tag**. Das heutige Tageskontingent ist um 00:00 festgeschrieben.
4. **Änderung während einer laufenden Sitzung:** Vorwarnung und Alarm werden aus der aktuellen Restzeit neu berechnet. Ist die Restzeit ≤ 0, klingelt der Alarm sofort und dann alle 5 min. Liegt sie unter der Schwelle, kommt die Vorwarnung sofort (höchstens einmal pro Sitzung). Steigt die Restzeit wieder über 0, verstummt der Alarm und wird neu geplant.
5. **Start** ist nur bei Restzeit > 0 möglich. Offline entscheidet der zuletzt synchronisierte Stand.
6. **Tag und Zeitzone:** Ein Tag geht von 00:00 bis 24:00 in **einer Familien-Zeitzone**, die am Server konfiguriert ist. Dauern sind tatsächlich verstrichene Minuten, die Sommerzeit spielt keine Rolle, Reisen werden ignoriert.
7. **Mitternacht:** Eine Sitzung wird auf beide Tage aufgeteilt. Ab 00:00 zählt das neue Tageskontingent, ein laufender Alarm kann dabei verstummen.
8. **Automatischer Stopp:** Erreicht die Überziehung eine einstellbare Grenze (Standard 60 min), wird die Sitzung automatisch beendet und als solche markiert. Der Alarm hört auf, und die Eltern werden benachrichtigt. Einen festen Stopp um Mitternacht gibt es nicht.
9. **Offline:** Start und Stopp funktionieren offline. Das Kindgerät rechnet mit dem zuletzt synchronisierten Stand und plant Vorwarnung und Alarm lokal. Online zählt die **Serverzeit beim Empfang**. Offline gepufferte Ereignisse werden später mit Gerätezeitstempel übertragen. Die Eltern sehen, seit wann der Stand des Kindgeräts unbekannt ist.
10. **Out of scope für v1:** nachträgliches Bearbeiten, Löschen oder Nachtragen von Sitzungen durch die Eltern.
