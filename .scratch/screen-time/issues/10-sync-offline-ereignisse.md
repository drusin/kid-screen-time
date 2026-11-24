# Wie werden offline gepufferte Start- und Stopp-Ereignisse mit dem Server zusammengeführt?

Type: grilling
Status: resolved
Assignee: Dawid Rusin
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

## Answer

Im Grilling mit dem Nutzer am 2026-11-24 entschieden.

1. **Sitzungs-Upsert statt Ereignis-Log:** Das Kindgerät erzeugt beim Start eine **Sitzungs-UUID** und überträgt immer den ganzen Zustand der Sitzung (`id`, Start, Ende oder leer). Der Server legt an oder aktualisiert. Doppelte Übertragungen ändern nichts. Lücken (Stopp ohne Start) gibt es nicht, weil ein Stopp den Start immer mitbringt. Eine Tabelle mit verarbeiteten Ereignis-IDs ist nicht nötig.
2. **Ein Weg für online und offline (Outbox):** Jede Änderung landet zuerst lokal (Room) in einer Outbox. Die UI zeigt sie sofort an, und der Alarm wird lokal geplant. Der Sync schickt alle offenen Sitzungen als ein Batch in Startreihenfolge und erhält den Stand-Snapshot zurück. Er läuft sofort, und wenn das scheitert, wiederholt ihn ein WorkManager-Job mit Netzwerk-Bedingung. Einen separaten Online-Pfad gibt es nicht.
3. **Zeit als Alter:** Das Gerät überträgt keine Uhrzeit, sondern das **Alter** des Zeitpunkts, gemessen mit der monotonen Uhr (`elapsedRealtime`). Der Server rechnet `Serverzeit beim Empfang − Alter`, online ist das Alter ≈ 0. Liegt zwischen Zeitpunkt und Sync ein Neustart, schickt das Gerät seine Uhrzeit, korrigiert um den Versatz aus dem letzten Snapshot. Der Server begrenzt jeden Zeitpunkt auf den Bereich [Ende der vorigen Sitzung, jetzt]: nichts in der Zukunft, keine Überlappung.
4. **Ein Start ist ein Fakt:** Der Server übernimmt jeden Start und lehnt keinen ab. „Kein Start bei Restzeit ≤ 0“ prüft das Kindgerät mit dem dort bekannten Stand. Ist nach dem Sync die Restzeit ≤ 0 (z. B. wegen einer inzwischen gebuchten Tagesanpassung), klingelt der Alarm sofort (Regel 4 aus [Wie verhalten sich Kontingent und Sitzung in den Randfällen?](04-domaenenregeln-randfaelle.md)).
5. **Das frühere Ende gewinnt:** Ein Stopp für eine bereits beendete Sitzung (z. B. nach einem Automatischen Stopp durch den Server) ändert nichts. Das Kindgerät plant den Automatischen Stopp mit `domain` zusätzlich lokal, damit der Alarm offline nicht endlos weiterklingelt. Weichen die Ergebnisse ab, gilt der Server.
6. **Ersetztes Kindgerät:** Bekommt das alte Gerät beim Sync `401`, verwirft es die Outbox und zeigt „Dieses Gerät ist entkoppelt“. Daten, die nur dort gepuffert waren, gehen bewusst verloren. Das neue Gerät übernimmt eine laufende Sitzung über die Sitzungs-UUID aus dem Snapshot.
7. **Keine nachgeholten Eltern-Benachrichtigungen:** Läuft eine nachträglich bekannte Sitzung beim Sync noch und ist schon in der Überziehung, kommt die Alarm-Benachrichtigung jetzt, einmal. Danach plant der Server normal weiter. Bereits beendete Offline-Sitzungen erscheinen nur im Verlauf, markiert als „offline erfasst“.
