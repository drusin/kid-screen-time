# Merkt der Server, wenn das Kindgerät verstummt, und wie erfahren es die Eltern?

Type: grilling
Status: resolved
Assignee: Dawid Rusin
Blocked by: -

## Question

Der Alarm hängt am Kindgerät. Wird die App deinstalliert, das Tablet ausgeschaltet oder eine Pflichteinstellung zurückgedreht (Exakte Alarme, Akku, Alarmlautstärke), merken die Eltern das bisher nicht. Die Einrichtungs-Checkliste zeigt Fehlendes nur am Kindgerät ([Welche App-Architektur und welcher Stack?](06-app-architektur-stack.md), Punkt 8), und die Geräteliste zeigt „zuletzt gesehen“ ([Wie werden Geräte gekoppelt und der Server abgesichert?](05-kopplung-sicherheit.md)).
- Gibt es ein regelmäßiges Lebenszeichen (WorkManager-Intervall? Mindestabstand bei Doze?) oder reicht „zuletzt gesehen“ aus den normalen Syncs?
- Meldet das Kindgerät den Zustand der Checkliste an den Server, und sieht ihn das Elterngerät?
- Ab wann gilt das Gerät als verstummt, und wird dann aktiv benachrichtigt oder nur angezeigt? Wie unterscheidet man „Tablet nachts aus“ von „App gelöscht“?
- Was ist, wenn eine Sitzung läuft und das Kindgerät verstummt (der Server benachrichtigt die Eltern ohnehin beim Alarm)?

## Answer

Im Grilling mit dem Nutzer am 2026-11-24 entschieden. Die Begriffe stehen in [GLOSSARY.md](../../../GLOSSARY.md) (Einrichtungs-Checkliste, Kindgerät).

1. **Kein eigenes Lebenszeichen.** „Zuletzt gesehen“ entsteht nur aus den normalen Syncs: nach Push, beim App-Start, beim Entsperren und bei der Outbox-Wiederholung ([Welcher Backend-Stack, welche Persistenz und welche API-Form?](07-backend-stack-persistenz.md), [Wie werden offline gepufferte Start- und Stopp-Ereignisse mit dem Server zusammengeführt?](10-sync-offline-ereignisse.md)). Es gibt keinen periodischen WorkManager-Job und keinen eigenen Endpunkt. Verworfen wurde ein stündlicher Sync, weil er für die Familie keinen Nutzen bringt.
2. **Checkliste geht an den Server.** Das Kindgerät prüft die prüfbaren Punkte der Einrichtungs-Checkliste ([Welche App-Architektur und welcher Stack?](06-app-architektur-stack.md), Punkt 8) bei **jedem Sync** neu, nicht nur beim App-Start. Es schickt die Liste der fehlenden Punkte mit, eine leere Liste heißt vollständig. Der Server speichert nur den letzten Stand, ohne Verlauf.
3. **Fehlende Punkte an die Eltern:** Bei jedem Übergang von „vollständig“ zu „etwas fehlt“ verschickt der Server genau eine Benachrichtigung an alle Elterngeräte. Der Kindername steht vorne, danach kommen die fehlenden Punkte, z. B. „Mia: Alarmlautstärke ist 0“. Eine weitere Benachrichtigung ist erst wieder möglich, nachdem eine vollständige Meldung kam. Am Elterngerät steht der Punkt rot im Statusbereich, bis die nächste vollständige Meldung eintrifft.
4. **Laufende Sitzung bei verstummtem Kindgerät:** Es gibt keine Sonderregel. Alarm-Benachrichtigung und Automatischer Stopp plant der Server ohnehin selbst.
5. **Out of scope:** Die Erkennung von Funkstille, also Schwellen ab wann das Kindgerät als verstummt gilt und eine Benachrichtigung bei Funkstille, gehört nicht zu dieser Map. Die Eltern sehen weiterhin nur „zuletzt gesehen“ in der Geräteliste ([Wie werden Geräte gekoppelt und der Server abgesichert?](05-kopplung-sicherheit.md)).
