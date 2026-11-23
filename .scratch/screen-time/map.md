# Map: Bildschirmzeit-App

Label: wayfinder:map

## Destination

Eine **umsetzungsreife Spezifikation plus Architekturentscheidung** für eine Familien-App, mit der Eltern ein tägliches Bildschirmzeit-Kontingent (Wochenvorlage + Tagesausnahmen) verwalten und das Kind am Tablet eine Sitzung startet und stoppt. Am Ende klingelt ein zuverlässiger Alarm auf dem Samsung-Tablet. Gebaut wird danach, nicht in dieser Map.

## Notes

- Domäne und Begriffe: siehe [GLOSSARY.md](../../GLOSSARY.md). Jede Session liest es und hält es aktuell (Skill `domain-modeling`).
- Kommunikation mit dem Nutzer auf **Deutsch**.
- Tracker: lokales Markdown (`.scratch/screen-time/issues/`).
- Feste Rahmenbedingungen (aus dem Charting):
  - Nur für die eigene Familie. Sideloading ist ok, kein Play Store.
  - Alle Geräte sind Android (Samsung-Tablet des Kindes, Android-Handys der Eltern).
  - Das Kontingent gilt für **alle** Bildschirme (Konsole, TV, …). Das Tablet ist Stoppuhr und Wecker, es misst keine Nutzung.
  - Der Alarm muss klingeln, auch wenn eine andere App im Vordergrund ist oder das Tablet im Standby ist. Er wiederholt sich alle 5 Minuten, solange die Sitzung läuft. Gesperrt wird nichts.
  - Eine Vorwarnung kommt vor Ablauf (einstellbar, Standard 5 min). Die Eltern-Handys bekommen eine Benachrichtigung beim Alarm.
  - Nur das Kind startet und stoppt Sitzungen, am Kindgerät.
  - Die Rolle wird pro Gerät beim Koppeln festgelegt (Kindgerät/Elterngerät). Eine App ist ok, eine Admin-PIN ist nicht nötig.
  - Der Server im Homelab (Docker, aus dem Internet erreichbar) ist die Quelle der Wahrheit. Das Kindgerät plant den Alarm zusätzlich lokal ab.
  - Funktioniert überall, wo Internet vorhanden ist.
  - **Keine laufenden Kosten** (keine kostenpflichtigen Dienste).
  - Erst ein Kind. Das Modell muss für mehrere Kinder erweiterbar sein.
  - Restzeit verfällt am Tagesende. Nach Ablauf zählt die Zeit als Überziehung weiter. Bonuszeit gibt es über eine Tagesausnahme.
  - Verlauf: einfache Liste von Sitzungen und Tagesverbrauch, keine Statistik.
  - Das Kind sieht Restzeit, Start/Stopp und das heutige Kontingent, aber keine Einstellungen.
- Stack-Präferenzen des Nutzers: kann JS/TS (Vue) und Java. Kotlin ist ok, weil Claude baut.
- Annahme (nicht explizit bestätigt): Im Homelab gibt es einen Reverse Proxy mit TLS und eine Domain.

## Decisions so far

<!-- eine Zeile pro geschlossenem Ticket -->

## Not yet specified

- **Deployment im Homelab**: Compose-Setup, Subdomain/Reverse-Proxy-Anbindung, Backups der Daten. Hängt von der Backend-Wahl ab.
- **Build & Release**: Wie wird die APK gebaut (lokal vs. GitHub Actions), und wie wird sie signiert? Hängt vom Stack und vom Update-Weg ab.
- **Synchronisation während einer laufenden Sitzung**: Was passiert genau, wenn die Eltern das Kontingent ändern, während der Timer läuft, oder das Tablet die Verbindung verliert? Protokoll und Mechanismus hängen von Push-Recherche und Architektur ab.
- **Mehrere Kinder**: Wie Kopplung und Eltern-UI aussehen, wenn es mehr als ein Kind gibt. Erst relevant, wenn Datenmodell und UI stehen.
- **Teststrategie**: Insbesondere, wie man das Alarmverhalten (Standby, Doze) verifiziert.

## Out of scope

- Sperren des Geräts oder Blockieren von Apps (Kiosk, Device Owner).
- Veröffentlichung im Play Store, iOS-Unterstützung.
- Automatische Erkennung der Nutzung (Bildschirmzeit wird manuell per Sitzung erfasst).
- Statistiken und Diagramme.
