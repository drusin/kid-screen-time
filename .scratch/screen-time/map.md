# Map: Bildschirmzeit-App

Label: wayfinder:map

## Destination

Eine **umsetzungsreife Spezifikation plus Architekturentscheidung** für eine Familien-App, mit der Eltern ein tägliches Bildschirmzeit-Kontingent (Wochenvorlage + Tagesanpassungen) verwalten und das Kind am Tablet eine Sitzung startet und stoppt. Am Ende klingelt ein zuverlässiger Alarm auf dem Samsung-Tablet. Gebaut wird danach, nicht in dieser Map.

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
  - Restzeit verfällt am Tagesende. Nach Ablauf zählt die Zeit als Überziehung weiter. Bonuszeit gibt es über eine Tagesanpassung.
  - Verlauf: einfache Liste von Sitzungen und Tagesverbrauch, keine Statistik.
  - Das Kind sieht Restzeit, Start/Stopp und das heutige Kontingent, aber keine Einstellungen.
- Stack-Präferenzen des Nutzers: kann JS/TS (Vue) und Java. Kotlin ist ok, weil Claude baut.
- Annahme (nicht explizit bestätigt): Im Homelab gibt es einen Reverse Proxy mit TLS und eine Domain.

## Decisions so far

<!-- eine Zeile pro geschlossenem Ticket -->

- [Wie klingelt ein Alarm auf Samsung-Android zuverlässig im Hintergrund und Standby?](issues/01-alarm-zuverlaessig-android.md): nur nativ, Kotlin-Alarmkern mit `setAlarmClock` + `USE_EXACT_ALARM` + Alarm-Stream. Eine PWA scheidet fürs Kindgerät aus, die UI kann trotzdem Capacitor/Vue sein
- [Wie erreichen Server-Ereignisse kostenlos die Android-Geräte?](issues/02-push-ohne-kosten.md): self-hosted ntfy + UnifiedPush als „resync“-Signal, FCM als Plan B
- [Wie kommen APK und Updates ohne Play Store auf die Geräte?](issues/03-apk-verteilung-updates.md): GitHub Releases + Obtainium, kostenloses „limited distribution“-Konto für Googles Entwicklerverifizierung ab 2027, ein Release-Keystore
- [Wie verhalten sich Kontingent und Sitzung in den Randfällen?](issues/04-domaenenregeln-randfaelle.md): Tagesanpassung als ±Delta nur für heute, Wochenvorlage gilt ab morgen, kein Start bei Restzeit ≤ 0, Neuberechnung des Alarms bei Änderung, Automatischer Stopp nach Überziehungsgrenze, eine Familien-Zeitzone, Start/Stopp offline-fähig
- [Wie werden Geräte gekoppelt und der Server abgesichert?](issues/05-kopplung-sicherheit.md): keine Konten, ein Geräte-Token mit Rolle pro Gerät, einmaliger Kopplungscode (QR, 10 min), Admin-Webformular (nur Heimnetz + Passwort) für das erste Elterngerät und die Recovery, ein Kindgerät pro Kind, WebPush-verschlüsselter Push-Payload
- [Welche App-Architektur und welcher Stack?](issues/06-app-architektur-stack.md): eine native Kotlin/Compose-App für beide Rollen (`minSdk 33`, Material 3, nur Deutsch), reines Kotlin-Modul `domain` für die Regeln, Room + Ktor Client, Eltern-Aktionen nur online, Einrichtungs-Checkliste am Kindgerät, ntfy-App als UnifiedPush-Verteiler
- [Welcher Backend-Stack, welche Persistenz und welche API-Form?](issues/07-backend-stack-persistenz.md): Kotlin + Ktor mit geteilten Modulen `domain`/`api` im Monorepo, SQLite im Volume, REST/JSON-Stand-Snapshot (Push nur als Signal), Compose mit Server + ntfy, Server plant zeitgesteuerte Ereignisse im Prozess und verschickt alle Eltern-Benachrichtigungen, JRE 25

## Not yet specified

- **Mehrere Kinder**: Wie Kopplung und Eltern-UI aussehen, wenn es mehr als ein Kind gibt. Erst relevant, wenn Datenmodell und UI stehen.
- **Lebenszeichen des Kindgeräts**: Der Server merkt, wenn das Kindgerät sich nicht mehr meldet (deinstalliert, ausgeschaltet), und benachrichtigt die Eltern? Ob und wie, ist noch unscharf.
- **Teststrategie**: Insbesondere, wie man das Alarmverhalten (Standby, Doze) verifiziert.

## Out of scope

- Sperren des Geräts oder Blockieren von Apps (Kiosk, Device Owner).
- Veröffentlichung im Play Store, iOS-Unterstützung.
- Automatische Erkennung der Nutzung (Bildschirmzeit wird manuell per Sitzung erfasst).
- Statistiken und Diagramme.
- Nachträgliches Bearbeiten, Löschen oder Nachtragen von Sitzungen durch die Eltern: für v1 bewusst ausgeschlossen (entschieden in [Wie verhalten sich Kontingent und Sitzung in den Randfällen?](issues/04-domaenenregeln-randfaelle.md)).
