# Wie sehen Kind- und Eltern-Oberfläche aus?

Type: prototype
Status: resolved
Assignee: Dawid Rusin
Blocked by: 04, 06

## Question

Wie sehen die Bildschirme aus? Kindgerät: Restzeit groß, Start/Stopp, heutiges Kontingent, Zustand „Überziehung“. Elterngerät: heutiger Stand, Wochenvorlage bearbeiten, Tagesanpassung (±) setzen, Verlauf, Geräte koppeln. Ein grober klickbarer Prototyp dient zum Reagieren und soll „native-like“ wirken.

## Answer

Am 2026-11-24 mit dem Nutzer am klickbaren Prototyp entschieden. Prototyp: Branch `prototype/ui-varianten`, Datei `prototypes/ui/index.html` (nur lokal, Push per SSH fehlgeschlagen), dazu das private Artifact https://claude.ai/artifact/KXwPLVjBveSzHwHhNd9ppD. Verglichen wurden je drei strukturell verschiedene Varianten pro Rolle.

1. **Kindgerät: Variante A „Ring“.** Oben eine App-Leiste mit Begrüßung und einem Symbol zur Einrichtungs-Checkliste. In der Mitte ein Fortschrittsring mit der Restzeit als große `mm:ss`-Anzeige und einem Zustandswort (übrig / läuft / gleich vorbei / drüber). Darunter das heutige Tageskontingent mit Aufschlüsselung („60 + 15 Bonus“) und die heute schon genutzte Zeit. Unten ein breiter Start/Stopp-Knopf in der Zustandsfarbe. Bei Überziehung ist der Ring voll rot und pulsiert, die Anzeige zählt mit „+“ hoch. Bei Restzeit ≤ 0 ohne laufende Sitzung ist der Knopf deaktiviert („Keine Zeit mehr übrig“). Verworfen: die Vollfläche (B) und Tank + Liste (C).
2. **Zustandsfarben** (offen aus [Welche App-Architektur und welcher Stack?](06-app-architektur-stack.md)): bereit = Primärfarbe, läuft = Grün, Vorwarnung = Bernstein, Überziehung = Rot, aufgebraucht = Grau. Die eigenen Farben gibt es für hell und dunkel, alles andere kommt aus Material 3 / Dynamic Color.
3. **Elterngerät: Variante B „Ein Bildschirm“.** Eine einzige scrollende Startseite: Status-Karte des Kindes (Restzeit, Fortschrittsbalken, „Sitzung läuft seit …“, Aufschlüsselung des Tageskontingents), darunter „Heute anpassen“ mit Stepper (±5 min), Vorschau des neuen Tageskontingents und „Buchen“ sowie „letzte zurücknehmen“ (Gegenbuchung). Dann eine Zeile „Wochenvorlage“, die einen Vollbild-Dialog öffnet, und die letzten Tage aus dem Verlauf mit Link zum ganzen Verlauf. Regeln (Vorwarnung, Grenze für den Automatischen Stopp) und Geräte (Liste, Entkoppeln, Koppeln) liegen im ⋮-Menü unter „Einstellungen & Geräte“. Verworfen: Tabs unten (A) und der Kind-Hub (C).
4. **Für beide Varianten gleich und so abgenommen:**
   - Der Alarm erscheint am Kindgerät als Vollbild („Zeit ist um!“, Überziehung, Knopf „Stopp“, darunter „Ton aus · klingelt in 5 min wieder“). Die Vorwarnung ist ein Ton plus Snackbar.
   - Fehlt etwas aus der Einrichtungs-Checkliste, zeigt das Kindgerät ein rotes Banner, das zur Checkliste führt. Offline steht ein Hinweis „gerechnet mit dem Stand von …“ da.
   - Am Elterngerät kommt beim Alarm und beim Automatischen Stopp eine Heads-up-Benachrichtigung. Offline gibt es eine Leiste „Offline · nur Ansicht“, und alle Aktionen sind deaktiviert. Die Status-Karte zeigt, seit wann der Stand des Kindgeräts unbekannt ist.
   - Koppeln: zuerst die Rolle wählen (Kindgerät für Kind X mit dem Hinweis, dass es das alte ersetzt, oder Elterngerät), dann QR-Code, Code zum Abtippen und Countdown.
   - Der Verlauf ist pro Tag „genutzt / Kontingent“ mit Überziehungs-Tag, darunter die Sitzungen (mit Markierung „Automatisch gestoppt“) und die Tagesanpassungen.
5. **Hinweis für mehrere Kinder:** Variante B ist auf ein Kind zugeschnitten. Für ein zweites Kind braucht sie eine Kind-Auswahl (z. B. in der App-Leiste). Das bleibt im Fog „Mehrere Kinder“.
