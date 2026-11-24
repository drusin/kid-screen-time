# Merkt der Server, wenn das Kindgerät verstummt, und wie erfahren es die Eltern?

Type: grilling
Status: open
Blocked by: -

## Question

Der Alarm hängt am Kindgerät. Wird die App deinstalliert, das Tablet ausgeschaltet oder eine Pflichteinstellung zurückgedreht (Exakte Alarme, Akku, Alarmlautstärke), merken die Eltern das bisher nicht. Die Einrichtungs-Checkliste zeigt Fehlendes nur am Kindgerät ([Welche App-Architektur und welcher Stack?](06-app-architektur-stack.md), Punkt 8), und die Geräteliste zeigt „zuletzt gesehen“ ([Wie werden Geräte gekoppelt und der Server abgesichert?](05-kopplung-sicherheit.md)).
- Gibt es ein regelmäßiges Lebenszeichen (WorkManager-Intervall? Mindestabstand bei Doze?) oder reicht „zuletzt gesehen“ aus den normalen Syncs?
- Meldet das Kindgerät den Zustand der Checkliste an den Server, und sieht ihn das Elterngerät?
- Ab wann gilt das Gerät als verstummt, und wird dann aktiv benachrichtigt oder nur angezeigt? Wie unterscheidet man „Tablet nachts aus“ von „App gelöscht“?
- Was ist, wenn eine Sitzung läuft und das Kindgerät verstummt (der Server benachrichtigt die Eltern ohnehin beim Alarm)?
