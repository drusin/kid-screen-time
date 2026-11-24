# Wie werden offline gepufferte Start- und Stopp-Ereignisse mit dem Server zusammengeführt?

Type: grilling
Status: open
Blocked by: 04, 07

## Question

Fachlich steht fest ([Wie verhalten sich Kontingent und Sitzung in den Randfällen?](04-domaenenregeln-randfaelle.md)): Start und Stopp funktionieren offline, online zählt die Serverzeit beim Empfang, und offline gepufferte Ereignisse werden später mit Gerätezeitstempel übertragen. Technisch steht fest ([Welcher Backend-Stack, welche Persistenz und welche API-Form?](07-backend-stack-persistenz.md)): REST/JSON, der Server liefert einen Stand-Snapshot inklusive Serverzeit, und der Server plant zeitgesteuerte Ereignisse selbst.

Offen ist das Protokoll:
- **Idempotenz:** Client-UUID pro Ereignis? Was passiert, wenn derselbe Start/Stopp doppelt ankommt?
- **Reihenfolge:** Werden Ereignisse einzeln oder als Batch übertragen? Wie verhält sich der Server bei Lücken (Stopp ohne bekannten Start)?
- **Zeit:** Wie werden Gerätezeitstempel gegen die Serverzeit plausibilisiert (Uhrabweichung, Offset aus dem letzten Snapshot, Zeitstempel in der Zukunft)?
- **Konflikte:**
  - Eine Tagesanpassung wurde inzwischen gebucht.
  - Der Server hat inzwischen einen Automatischen Stopp ausgelöst, während das Tablet offline weiterlief.
  - Das Kindgerät wurde zwischenzeitlich ersetzt.
- **Folgen:** Kommen Eltern-Benachrichtigungen (Alarm, Automatischer Stopp) nachträglich, wenn eine offline gelaufene Sitzung erst später bekannt wird?
