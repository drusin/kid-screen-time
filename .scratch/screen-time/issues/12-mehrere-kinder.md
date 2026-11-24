# Was muss v1 für mehrere Kinder können?

Type: grilling
Status: resolved
Assignee: Dawid Rusin
Blocked by: -

## Question

Erst gibt es ein Kind, das Modell soll aber für mehrere erweiterbar sein. Das Datenmodell hängt schon alles an eine `child_id` ([Welcher Backend-Stack, welche Persistenz und welche API-Form?](07-backend-stack-persistenz.md)), und jedes Kind hat genau ein Kindgerät ([Wie werden Geräte gekoppelt und der Server abgesichert?](05-kopplung-sicherheit.md)). Offen ist:
- Baut v1 die Mehrkind-Fähigkeit in UI und API schon ein, oder nur im Datenmodell (und die Oberfläche kommt später)?
- Wo und wie wird ein Kind angelegt, benannt, umbenannt oder entfernt (Eltern-App, Admin-Webformular, beim Koppeln des Kindgeräts)?
- Wie wählt die Eltern-Oberfläche (ein scrollender Bildschirm, siehe [Wie sehen Kind- und Eltern-Oberfläche aus?](08-ui-prototyp.md)) das Kind: Tabs, Auswahl oben, Übersicht aller Kinder?
- Welche Einstellungen gelten pro Kind und welche pro Familie (Wochenvorlage, Vorwarnschwelle, Überziehungsgrenze, Zeitzone)?
- Welche Eltern-Benachrichtigungen nennen das Kind, und wie sieht der Payload-Text aus?

## Answer

Im Grilling mit dem Nutzer am 2026-11-24 entschieden. Die Begriffe stehen in [GLOSSARY.md](../../../GLOSSARY.md) (Kind, Elterngerät).

1. **Umfang in v1:** Datenmodell **und API** können mehrere Kinder: Die Pfade enthalten das Kind, und der Eltern-Snapshot liefert eine Liste von Kindern. Die Eltern-UI zeigt in v1 genau ein Kind, ohne Kind-Auswahl und ohne „weiteres Kind anlegen“. Das kommt erst mit dem zweiten Kind, dann ohne API-Bruch. Verworfen: nur im Datenmodell (späterer API-Bruch) und volle Mehrkind-UI in v1 (Aufwand ohne Nutzen).
2. **Pro Kind:** Wochenvorlage, Tagesanpassungen, Vorwarnschwelle, Überziehungsgrenze, Verlauf, Kindgerät. **Pro Familie:** die Zeitzone. Die Elterngeräte gehören zu allen Kindern und bekommen die Benachrichtigungen aller Kinder. **Fest:** Die Alarmwiederholung alle 5 Minuten ist nicht einstellbar.
3. **Kind anlegen:** Beim Erzeugen eines Kopplungscodes für ein Kindgerät in der Eltern-App. Gibt es noch kein Kind, fragt die App nach dem Namen und legt das Kind an. Umbenannt wird über das ⋮-Menü bei „Regeln“. Kinder entfernen gibt es in v1 nicht. Entkoppeln betrifft nur das Gerät, das Kind und sein Verlauf bleiben.
4. **Benachrichtigungen:** Der Name des Kindes steht immer vorne im Text, z. B. „Mia: Kontingent abgelaufen (seit 14:32)“ oder „Mia: Automatischer Stopp nach 60 min Überziehung“.
