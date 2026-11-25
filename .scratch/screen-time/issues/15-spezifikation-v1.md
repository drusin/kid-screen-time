# Spezifikation v1: Bildschirmzeit-App

Type: spec
Label: ready-for-agent
Status: open
Blocked by: -

Quelle: die Map [Bildschirmzeit-App](../map.md) mit den Tickets 01–14. Die Begriffe stehen im [Glossar](../../../GLOSSARY.md) und werden hier genau so verwendet.

## Problemstellung

Wir Eltern wollen die tägliche Bildschirmzeit unseres Kindes begrenzen, und zwar über **alle** Bildschirme (Tablet, Konsole, Fernseher). Heute geht das nur per Zuruf und Küchenwecker: Niemand weiß genau, wie viel heute schon verbraucht ist, Bonuszeit wird vergessen oder doppelt gegeben, und der Wecker klingelt nicht, wenn das Kind gerade am Tablet spielt oder das Tablet im Standby liegt. Kommerzielle Kinderschutz-Apps sperren Geräte oder messen nur ein einzelnes Gerät, kosten laufend Geld oder schicken Familiendaten in fremde Clouds. Das wollen wir nicht.

Wir brauchen eine Stoppuhr mit verlässlichem Wecker, die das Kind selbst bedient, mit einem Kontingent, das wir Eltern vom Handy aus festlegen und spontan anpassen können, und mit einer Nachricht an uns, wenn die Zeit um ist.

## Lösung

Eine Android-App für die ganze Familie plus ein kleiner Server im Homelab.

- **Am Tablet des Kindes (Kindgerät)** sieht das Kind seine Restzeit als großen Ring, startet eine Sitzung, wenn es vor einen Bildschirm geht, und stoppt sie danach. Kurz vor Ablauf kommt eine Vorwarnung. Bei Restzeit 0 klingelt ein Alarm im Vollbild, auch im Standby, bei „Nicht stören“ und über jeder anderen App, und wiederholt sich alle 5 Minuten, bis das Kind stoppt. Läuft die Sitzung zu lange in der Überziehung weiter, wird sie automatisch gestoppt. Gesperrt wird nichts. Start und Stopp funktionieren auch offline.
- **Auf den Handys der Eltern (Elterngeräte)** sehen die Eltern den aktuellen Stand, buchen Bonus- oder Abzugsminuten für heute, pflegen die Wochenvorlage, stellen Vorwarnung und Überziehungsgrenze ein, sehen den Verlauf und koppeln oder entkoppeln Geräte. Bei Alarm, Automatischem Stopp und fehlender Einrichtung am Tablet bekommen sie eine Benachrichtigung.
- **Der Server** im Homelab ist die Quelle der Wahrheit, plant Eltern-Benachrichtigungen selbst und stößt die Geräte über einen selbst gehosteten Push-Dienst zum Abgleich an. Es entstehen keine laufenden Kosten.

## User Stories

### Kind: Sitzung und Restzeit

1. Als Kind möchte ich meine Restzeit für heute groß auf dem Tablet sehen, damit ich weiß, wie viel Bildschirmzeit ich noch habe.
2. Als Kind möchte ich mein heutiges Tageskontingent mit Aufschlüsselung sehen (z. B. „60 + 15 Bonus“), damit ich verstehe, woher meine Zeit kommt.
3. Als Kind möchte ich sehen, wie viel Zeit ich heute schon genutzt habe, damit ich mir die Zeit einteilen kann.
4. Als Kind möchte ich mit einem großen Knopf eine Sitzung starten, wenn ich vor einen Bildschirm gehe (Tablet, Konsole, Fernseher), damit meine Zeit gezählt wird.
5. Als Kind möchte ich mit demselben Knopf die Sitzung stoppen, wenn ich fertig bin, damit mir die restliche Zeit für später bleibt.
6. Als Kind möchte ich während einer Sitzung sehen, wie die Restzeit herunterzählt, damit ich weiß, wann Schluss ist.
7. Als Kind möchte ich an Farbe und Zustandswort (übrig / läuft / gleich vorbei / drüber) erkennen, in welchem Zustand ich bin, damit ich das auf einen Blick verstehe.
8. Als Kind möchte ich, dass der Start-Knopf deaktiviert ist und „Keine Zeit mehr übrig“ zeigt, wenn meine Restzeit 0 ist, damit klar ist, warum ich nicht starten kann.
9. Als Kind möchte ich eine Vorwarnung (kurzer Ton und Hinweis) bekommen, wenn meine Restzeit die eingestellte Schwelle erreicht, damit ich mein Spiel noch beenden kann.
10. Als Kind möchte ich die Vorwarnung für dasselbe Ende meiner Zeit nur einmal bekommen, nach einem Bonus aber wieder kurz vor dem neuen Ende, damit sie nicht nervt und ich trotzdem rechtzeitig gewarnt werde.
11. Als Kind möchte ich bei Restzeit 0 einen Alarm im Vollbild sehen und hören („Zeit ist um!“), auch wenn gerade ein Spiel oder Video läuft, damit ich es nicht übersehe.
12. Als Kind möchte ich im Alarm-Vollbild direkt „Stopp“ drücken können, damit ich die Sitzung ohne Umweg beende.
13. Als Kind möchte ich den Ton des Alarms ausschalten können, mit dem Hinweis, dass er in 5 Minuten wieder klingelt, damit ich kurz zu Ende spielen kann, ohne dass es dauernd klingelt.
14. Als Kind möchte ich bei Überziehung sehen, wie lange ich schon drüber bin (Anzeige mit „+“, roter pulsierender Ring), damit ich weiß, was los ist.
15. Als Kind möchte ich, dass meine Sitzung offline genauso startet und stoppt, damit es auch ohne WLAN funktioniert.
16. Als Kind möchte ich offline einen Hinweis „gerechnet mit dem Stand von …“ sehen, damit ich weiß, dass sich die Zahl nach dem Abgleich noch ändern kann.
17. Als Kind möchte ich, dass ein Bonus, den meine Eltern gerade gebucht haben, nach wenigen Sekunden bei mir erscheint, damit ich gleich weitermachen kann.
18. Als Kind möchte ich, dass ein laufender Alarm verstummt, wenn meine Eltern mir Bonuszeit geben, damit ich nicht weiter gestört werde.
19. Als Kind möchte ich, dass eine Sitzung über Mitternacht ab 00:00 vom neuen Tag abgezogen wird, damit der neue Tag mit meinem neuen Kontingent rechnet.
20. Als Kind möchte ich keine Einstellungen sehen oder ändern können, damit klar ist, dass die Regeln von meinen Eltern kommen.

### Kindgerät: Zuverlässigkeit und Einrichtung

21. Als Elternteil möchte ich, dass der Alarm auch klingelt, wenn das Tablet im Standby/Doze liegt, damit sich das Kind nicht durch Bildschirm-aus herausreden kann.
22. Als Elternteil möchte ich, dass der Alarm auch bei „Nicht stören“ und lautlos geschaltetem Medienton über den Alarm-Kanal klingelt, damit er hörbar ist.
23. Als Elternteil möchte ich, dass der Alarm nach einem Neustart des Tablets neu geplant wird, damit ein Neustart ihn nicht abschaltet.
24. Als Elternteil möchte ich, dass sich der Alarm alle 5 Minuten wiederholt, solange die Sitzung läuft, damit das Kind es nicht einfach ignorieren kann.
25. Als Elternteil möchte ich, dass die Sitzung automatisch stoppt, wenn die Überziehung die eingestellte Grenze erreicht, damit eine vergessene Sitzung nicht ewig weiterläuft und das Tablet nicht stundenlang klingelt.
26. Als Elternteil möchte ich, dass der Automatische Stopp auch dann am Tablet greift, wenn es offline ist, damit der Alarm nicht endlos klingelt.
27. Als Elternteil möchte ich am Tablet eine Einrichtungs-Checkliste mit allen nötigen Einstellungen (Exakte Alarme, Vollbild-Benachrichtigung, Benachrichtigungen, Akku „Nicht eingeschränkt“, Alarmlautstärke > 0, verbundener Push-Verteiler) sehen, damit ich das Tablet einmal richtig einrichte.
28. Als Elternteil möchte ich zu jedem fehlenden Punkt einen direkten Link in die passende Systemeinstellung, damit ich nicht suchen muss.
29. Als Elternteil möchte ich nicht prüfbare Punkte (z. B. „Nie schlafende Apps“, Alarme bei „Nicht stören“) als Hinweis zum Abhaken sehen, damit ich nichts vergesse.
30. Als Kind und Elternteil möchte ich auf dem Hauptbildschirm ein rotes Banner sehen, wenn ein prüfbarer Punkt fehlt, damit es auffällt.

### Eltern: Status und Tagesanpassung

31. Als Elternteil möchte ich auf meinem Handy auf einen Blick Restzeit, Fortschritt, Tageskontingent mit Aufschlüsselung und eine laufende Sitzung („läuft seit …“) sehen, damit ich weiß, wo mein Kind steht.
32. Als Elternteil möchte ich sehen, seit wann der Stand des Kindgeräts unbekannt ist, damit ich einschätzen kann, wie aktuell die Anzeige ist.
33. Als Elternteil möchte ich mit einem Stepper (±5 min) eine Tagesanpassung für heute vorbereiten und eine Vorschau des neuen Tageskontingents sehen, bevor ich buche, damit ich mich nicht verrechne.
34. Als Elternteil möchte ich Bonuszeit (+) und Abzüge (−) buchen, damit ich auf den Tag reagieren kann.
35. Als Elternteil möchte ich, dass mehrere Tagesanpassungen eines Tages sich addieren, damit ich nachbessern kann.
36. Als Elternteil möchte ich die letzte Tagesanpassung zurücknehmen (als Gegenbuchung), damit ein Vertipper schnell korrigiert ist.
37. Als Elternteil möchte ich, dass eine Tagesanpassung sofort wirkt, auch während einer laufenden Sitzung (Vorwarnung und Alarm neu berechnet), damit das Kind gleich die neue Zeit hat.
38. Als Elternteil möchte ich, dass das Tageskontingent nie unter 0 fällt, damit die Anzeige keinen Unsinn zeigt.
39. Als Elternteil möchte ich, dass ein Abzug unter den bisherigen Verbrauch bei laufender Sitzung sofort den Alarm auslöst, damit die Regel sofort gilt.

### Eltern: Wochenvorlage und Regeln

40. Als Elternteil möchte ich für jeden Wochentag (Mo–So) ein Standard-Kontingent festlegen, damit ich nicht jeden Tag etwas einstellen muss.
41. Als Elternteil möchte ich die Wochenvorlage in einem Vollbild-Dialog bearbeiten, damit ich alle sieben Tage gleichzeitig sehe.
42. Als Elternteil möchte ich, dass eine Änderung der Wochenvorlage erst ab morgen gilt, damit sich das heutige Kontingent nicht überraschend ändert (dafür gibt es die Tagesanpassung).
43. Als Elternteil möchte ich die Vorwarnschwelle einstellen (Standard 5 min), damit sie zu unserem Kind passt.
44. Als Elternteil möchte ich die Überziehungsgrenze für den Automatischen Stopp einstellen (Standard 60 min), damit vergessene Sitzungen enden.
45. Als Elternteil möchte ich den Namen des Kindes ändern können, damit Benachrichtigungen den richtigen Namen tragen.
46. Als Elternteil möchte ich Regeln und Geräte hinter dem ⋮-Menü „Einstellungen & Geräte“ finden, damit die Startseite übersichtlich bleibt.

### Eltern: Benachrichtigungen

47. Als Elternteil möchte ich eine Heads-up-Benachrichtigung bekommen, wenn die Restzeit meines Kindes während einer Sitzung 0 erreicht („Mia: Kontingent abgelaufen (seit 14:32)“), damit ich eingreifen kann.
48. Als Elternteil möchte ich diese Benachrichtigung auch bekommen, wenn das Tablet gerade offline ist, damit ich mich nicht auf das Tablet verlassen muss.
49. Als Elternteil möchte ich eine Benachrichtigung beim Automatischen Stopp bekommen („Mia: Automatischer Stopp nach 60 min Überziehung“), damit ich weiß, dass das Kind nicht selbst gestoppt hat.
50. Als Elternteil möchte ich genau eine Benachrichtigung bekommen, wenn am Tablet eine Einstellung der Checkliste verloren geht („Mia: Alarmlautstärke ist 0“), damit ich es beheben kann, ohne zugespammt zu werden.
51. Als Elternteil möchte ich, dass der fehlende Punkt rot in meinem Status steht, bis das Tablet wieder vollständig meldet, damit ich es nicht vergesse.
52. Als Elternteil möchte ich, dass der Name des Kindes vorne in jeder Benachrichtigung steht, damit es bei mehreren Kindern später eindeutig ist.
53. Als Elternteil möchte ich keine nachgeholten Benachrichtigungen für Sitzungen, die offline schon beendet wurden, damit ich nicht im Nachhinein alarmiert werde.
54. Als Elternteil möchte ich, dass alle Elterngeräte dieselben Benachrichtigungen bekommen, damit beide Eltern informiert sind.

### Eltern: Verlauf

55. Als Elternteil möchte ich die letzten Tage auf der Startseite sehen („genutzt / Kontingent“, Überziehung markiert), damit ich Muster bemerke.
56. Als Elternteil möchte ich den ganzen Verlauf öffnen, mit den Sitzungen pro Tag und den Tagesanpassungen, damit ich nachvollziehen kann, was passiert ist.
57. Als Elternteil möchte ich im Verlauf sehen, welche Sitzungen automatisch gestoppt wurden, damit ich vergessene Sitzungen erkenne.
58. Als Elternteil möchte ich im Verlauf sehen, welche Sitzungen offline erfasst wurden, damit ich verstehe, warum sie später auftauchen.
59. Als Elternteil möchte ich, dass eine Sitzung über Mitternacht im Verlauf korrekt auf beide Tage aufgeteilt ist, damit die Tagesverbräuche stimmen.

### Eltern: offline

60. Als Elternteil möchte ich offline den letzten bekannten Stand lesen können, mit der Leiste „Offline · nur Ansicht“, damit ich trotzdem informiert bin.
61. Als Elternteil möchte ich, dass alle Aktionen offline deaktiviert sind, damit ich nicht glaube, etwas gebucht zu haben, was nie ankommt.

### Kopplung und Geräte

62. Als Elternteil möchte ich das erste Elterngerät über ein Admin-Formular am Server im Heimnetz koppeln, damit niemand aus dem Internet das kann.
63. Als Elternteil möchte ich, dass das Admin-Formular zusätzlich ein Passwort verlangt, damit nicht jeder im Heimnetz (auch nicht das Kind) Eltern-Geräte koppeln kann.
64. Als Elternteil möchte ich am Elterngerät einen Kopplungscode für ein weiteres Elterngerät erzeugen, damit der andere Elternteil mitmachen kann.
65. Als Elternteil möchte ich beim Koppeln zuerst die Rolle wählen (Kindgerät für Kind X oder Elterngerät), damit die Rolle eindeutig festgelegt ist.
66. Als Elternteil möchte ich beim ersten Kindgerät den Namen des Kindes eingeben, damit das Kind dabei angelegt wird.
67. Als Elternteil möchte ich den Kopplungscode als QR-Code (mit Server-URL) und als kurzen Text zum Abtippen sehen, mit Countdown, damit das Koppeln einfach ist und ich weiß, wie lange er gilt.
68. Als Elternteil möchte ich, dass ein Kopplungscode einmalig ist und nach 10 Minuten verfällt, damit ein abfotografierter Code später nichts nützt.
69. Als Elternteil möchte ich beim Koppeln eines neuen Kindgeräts den Hinweis sehen, dass es das alte ersetzt, damit ich nicht überrascht bin.
70. Als Elternteil möchte ich, dass beim Ersetzen des Kindgeräts eine laufende Sitzung am neuen Gerät weiterläuft, damit nichts verloren geht.
71. Als Elternteil möchte ich eine Geräteliste mit Name, Rolle, Kopplungsdatum und „zuletzt gesehen“ sehen, damit ich weiß, welche Geräte Zugang haben.
72. Als Elternteil möchte ich jedes Gerät entkoppeln können (auch mein eigenes), damit ein verlorenes Handy keinen Zugang mehr hat.
73. Als Elternteil möchte ich, dass das letzte Elterngerät nicht entkoppelt werden kann, damit wir uns nicht aussperren.
74. Als Elternteil möchte ich, wenn alle Elterngeräte verloren sind, über das Admin-Formular ein neues Elterngerät koppeln, damit ich wieder Zugang bekomme.
75. Als Nutzer eines entkoppelten Geräts möchte ich „Gerät wurde entkoppelt“ sehen, damit ich verstehe, warum die App nicht mehr geht.
76. Als Kind möchte ich am Kindgerät nichts außer meinen eigenen Sitzungen und meinem Stand tun können, damit klar ist, dass ich die Regeln nicht ändern kann; als Elternteil möchte ich, dass der Server das bei jedem Aufruf durchsetzt.
77. Als Elternteil möchte ich, dass Fremde aus dem Internet weder Codes erraten noch Daten lesen können (Rate-Limit, lange Tokens, TLS), damit die Familie geschützt ist.
78. Als Elternteil möchte ich, dass der Push-Dienst nur verschlüsselte Nachrichten sieht und von außen nicht zum Senden missbraucht werden kann, damit keine Familiendaten im Klartext dort liegen.

### Betrieb, Verteilung und Updates

79. Als Betreiber möchte ich den Server als Compose-Stack (Server + ntfy) in mein bestehendes Homelab mit Traefik einhängen, damit er wie meine anderen Dienste läuft.
80. Als Betreiber möchte ich die Konfiguration (Admin-Passwort, Familien-Zeitzone, öffentliche URLs) über Compose und `.env` setzen, und der Server soll nicht starten, wenn ein Pflichtwert fehlt, damit Fehlkonfigurationen sofort auffallen.
81. Als Betreiber möchte ich, dass Datenbank-Migrationen beim Start automatisch laufen, damit ein Update nur „Version hochsetzen, pull, up“ ist.
82. Als Betreiber möchte ich, dass der Server mit der vorigen App-Version kompatibel bleibt, damit ich erst den Server und dann die App aktualisieren kann.
83. Als Betreiber möchte ich, dass ein Tag `vX.Y.Z` die signierte APK als GitHub Release und das Server-Image auf GHCR baut, damit ein Release ein einziger Schritt ist.
84. Als Betreiber möchte ich, dass kein Release entsteht, wenn Tests oder Lint fehlschlagen, damit keine kaputte Version auf die Geräte kommt.
85. Als Elternteil möchte ich die App über Obtainium aus den GitHub Releases installieren und aktualisieren, damit es ohne Play Store und ohne Kosten geht.
86. Als Entwickler möchte ich einen Debug-Build parallel zur Release-App installieren können, mit einem versteckten Knopf „Testsitzung mit 2 min Kontingent“, damit ich den Alarm in Minuten am echten Tablet prüfen kann.

### Mehrere Kinder (vorbereitet)

87. Als Elternteil möchte ich, dass später ein zweites Kind hinzukommen kann, ohne dass die App oder die Daten neu aufgesetzt werden müssen, damit die Lösung mitwächst.
88. Als Elternteil möchte ich, dass jedes Kind eigene Wochenvorlage, Tagesanpassungen, Vorwarnschwelle, Überziehungsgrenze, Verlauf und genau ein Kindgerät hat, damit die Kinder unabhängig sind.

## Umsetzungsentscheidungen

### Gesamtarchitektur

- **Monorepo, ein Gradle-Build, vier Module:**
  - `domain`: reines Kotlin ohne Android- und Server-Abhängigkeit. Enthält alle Zeit- und Kontingentregeln. Wird von App und Server geteilt, damit die Regeln nur einmal existieren.
  - `api`: reines Kotlin mit kotlinx.serialization. DTOs und Pfade des REST-Vertrags, von App und Server geteilt.
  - `app`: die Android-App (Kotlin, Jetpack Compose, Material 3 mit Dynamic Color, `minSdk 33`, nur Deutsch, Texte trotzdem als String-Ressourcen). Package `io.github.drusin.kidscreentime`, Debug-Builds mit Suffix `.debug` und Namen „Bildschirmzeit Dev“.
  - `server`: Kotlin + Ktor, SQLite, Fat-JAR auf `eclipse-temurin:25-jre`.
- **Eine App für beide Rollen.** Die Rolle (Kindgerät für ein Kind / Elterngerät) kommt aus der Kopplung und ändert sich danach nicht. Vor der Kopplung zeigt die App nur „Gerät koppeln“ (QR scannen oder Code eintippen).
- **Server ist die Quelle der Wahrheit.** Das Kindgerät rechnet zusätzlich lokal mit `domain` und dem zuletzt synchronisierten Stand, um Vorwarnung, Alarm und Automatischen Stopp offline zu planen. Weichen Ergebnisse ab, gilt der Server.
- **Injizierte Uhren überall.** `domain`, Server und App bekommen Wanduhr und monotone Uhr injiziert. Direkte Aufrufe der Systemuhr im Produktivcode sind verboten und werden per Architekturtest (z. B. Konsist) geprüft.

### Modul `domain` (die tiefste und wichtigste Schnittstelle)

Eine kleine Schnittstelle mit viel Verhalten dahinter: Eingaben sind der Stand eines Kindes und ein Zeitpunkt, Ausgaben sind die abgeleiteten Werte und die nächsten fälligen Zeitpunkte. Kein I/O, keine eigene Uhr.

- **Eingaben (Stand eines Kindes):** Familien-Zeitzone, versionierte Wochenvorlage (je Version „gültig ab“-Datum und sieben Minutenwerte), Tagesanpassungen (Datum, Delta in Minuten, Zeitpunkt der Buchung), Sitzungen (ID, Start, Ende oder offen, Grund für das Ende), Vorwarnschwelle, Überziehungsgrenze.
- **Abgeleitete Werte für einen Zeitpunkt:** Tag (in Familien-Zeitzone), Tageskontingent mit Aufschlüsselung (Vorlage + Summe der Anpassungen, nie < 0), heutiger Verbrauch, Restzeit, Überziehung, Zustand (bereit / läuft / Vorwarnung / Überziehung / aufgebraucht), ob ein Start erlaubt ist (nur bei Restzeit > 0).
- **Nächste fällige Zeitpunkte bei laufender Sitzung:** Vorwarnung (sofort, wenn die Restzeit schon unter der Schwelle liegt; einmal je Ende des Kontingents, nach einem Bonus also wieder die Schwelle vor dem neuen Ende, nach einem Abzug nicht noch einmal), Alarm bei Restzeit 0 (sofort, wenn ≤ 0) und dessen Wiederholungen alle 5 Minuten (fest, nicht einstellbar), Automatischer Stopp bei Erreichen der Überziehungsgrenze (mit dem Endzeitpunkt der Sitzung), nächste Mitternacht.
- **Regeln, die `domain` kapselt:**
  - Tageskontingent = Wert der Wochenvorlage-Version, die um 00:00 des Tages galt, plus Summe der Tagesanpassungen dieses Tages, mindestens 0. Eine Vorlagen-Änderung gilt ab dem nächsten Tag.
  - Eine Sitzung über Mitternacht wird beim Lesen auf beide Tage aufgeteilt. Ab 00:00 zählt das neue Tageskontingent, ein laufender Alarm kann dabei verstummen.
  - Dauern sind tatsächlich verstrichene Minuten. Tage mit Zeitumstellung haben 23 bzw. 25 Stunden.
  - Steigt die Restzeit durch eine Tagesanpassung wieder über 0, ist kein Alarm mehr fällig, und Vorwarnung/Alarm werden neu berechnet.
- **Verlauf:** Liste der Tage mit Verbrauch, Tageskontingent, Überziehung, den (aufgeteilten) Sitzungen und den Tagesanpassungen. Der Server speichert alle Sitzungen und Tagesanpassungen unbegrenzt (nichts wird gelöscht); die App zeigt im vollen Verlauf die letzten 90 Tage, auf der Startseite die letzten 7.

### Modul `api`: REST-Vertrag

REST/JSON, Bearer-Token im Header, Kind-ID in den Pfaden (mehrkindfähig). Die genauen Pfadnamen legt die Umsetzung fest. Der Vertrag umfasst:

- **Kopplung (ohne Token):** Kopplungscode einlösen, Gerätename und Push-Registrierung mitschicken. Antwort: Geräte-Token, Rolle, ggf. Kind.
- **Kindgerät – Sync:** ein Aufruf, der alle offenen Sitzungen aus der Outbox als Batch (in Startreihenfolge) und die Liste der fehlenden Checklisten-Punkte mitbringt und den **Kind-Snapshot** zurückgibt. Ein Sync ohne Sitzungen ist der normale Abgleich.
- **Kind-Snapshot:** Kind (ID, Name), Tageskontingent mit Aufschlüsselung, Verbrauch heute, laufende Sitzung (mit Sitzungs-UUID), Vorwarnschwelle, Überziehungsgrenze, Familien-Zeitzone, Serverzeit, und alles, was `domain` am Gerät zum lokalen Rechnen bis Mitternacht und darüber hinaus braucht (gültige Wochenvorlage für heute und morgen). Immer der vollständige Stand, keine Deltas.
- **Elterngerät – Eltern-Snapshot:** Liste der Kinder (in v1 genau eins), je Kind der Status (wie oben), „Stand des Kindgeräts bekannt seit“, fehlende Checklisten-Punkte, Wochenvorlage, Regeln, letzte Tage des Verlaufs; dazu Geräteliste (Name, Rolle, Kind, Kopplungsdatum, zuletzt gesehen) und Serverzeit.
- **Elterngerät – Aktionen (nur online):** Tagesanpassung für heute buchen (Delta, auch negativ als Gegenbuchung), Wochenvorlage setzen, Regeln setzen (Vorwarnschwelle, Überziehungsgrenze, Name des Kindes), Kopplungscode erzeugen (Rolle; bei Kindgerät das Kind oder, wenn es noch keins gibt, der Name eines neuen Kindes), Gerät entkoppeln, vollständigen Verlauf eines Kindes lesen. Jede Aktion liefert den neuen Eltern-Snapshot zurück.
- **Push-Registrierung aktualisieren:** UnifiedPush-Endpoint und WebPush-Schlüssel (öffentlicher Schlüssel + Auth-Secret) des Geräts, falls sie sich ändern.
- **Fehler:** `401` heißt „entkoppelt oder ungültig“ (Gerät zeigt „Gerät wurde entkoppelt“), `403` heißt falsche Rolle, `429` heißt Rate-Limit.
- **Kompatibilität:** Der Server versteht immer auch die Requests der vorigen App-Version. Neue Felder sind optional.

### Sync-Protokoll (Kindgerät ↔ Server)

- **Sitzungs-Upsert statt Ereignis-Log.** Das Kindgerät erzeugt beim Start eine Sitzungs-UUID und überträgt immer den ganzen Zustand der Sitzung (ID, Start, Ende oder leer). Der Server legt an oder aktualisiert. Doppelte Übertragungen sind wirkungslos. Ein Stopp bringt seinen Start immer mit.
- **Outbox als einziger Weg.** Jede Änderung landet zuerst lokal (Room). Die UI zeigt sie sofort, der Alarm wird sofort lokal geplant. Der Sync läuft sofort; scheitert er, wiederholt ihn ein WorkManager-Job mit Netzwerk-Bedingung. Kein separater Online-Pfad.
- **Zeit als Alter.** Das Gerät überträgt pro Zeitpunkt das Alter, gemessen mit der monotonen Uhr. Der Server rechnet „Serverzeit beim Empfang − Alter“. Lag ein Neustart dazwischen, schickt das Gerät seine Wanduhrzeit, korrigiert um den Versatz aus dem letzten Snapshot. Der Server begrenzt jeden Zeitpunkt auf [Ende der vorigen Sitzung, jetzt].
- **Ein Start ist ein Fakt.** Der Server lehnt keinen Start ab. „Kein Start bei Restzeit ≤ 0“ prüft nur das Kindgerät mit seinem Stand. Ist die Restzeit nach dem Sync ≤ 0, klingelt der Alarm sofort.
- **Das frühere Ende gewinnt.** Ein Stopp für eine schon beendete Sitzung (z. B. nach Automatischem Stopp durch den Server) ändert nichts.
- **Offline erfasst:** Eine Sitzung, deren Start der Server erst mit Verzögerung erfährt, wird als „offline erfasst“ markiert.
- **Ersetztes Kindgerät:** Bekommt es `401`, verwirft es die Outbox. Das neue Kindgerät übernimmt eine laufende Sitzung über die Sitzungs-UUID im Snapshot.
- **Wann gesynct wird:** nach jedem Push-Signal, beim App-Start, beim Entsperren des Geräts, nach jeder Start/Stopp-Aktion und bei der Outbox-Wiederholung. Kein periodischer Job. „Zuletzt gesehen“ entsteht aus diesen Syncs.
- **Elterngerät:** Abgleich nach Push, beim App-Start und nach jeder eigenen Aktion. Lokaler Cache nur zum Lesen.

### Server

- **Persistenz:** SQLite-Datei im Volume, Migrationen beim Start. Bibliothek (z. B. Exposed oder JDBC + Flyway) wählt die Umsetzung.
- **Datenmodell (Grundsätze):**
  - Alles hängt an einer Kind-ID.
  - Kinder: ID, Name, Vorwarnschwelle, Überziehungsgrenze.
  - Wochenvorlage-Versionen: Kind, „gültig ab“-Datum, sieben Minutenwerte. Eine Änderung legt eine Version mit „gültig ab morgen“ an. Kein Mitternachts-Job.
  - Tagesanpassungen: nur angehängt (Kind, Tag, Delta, Buchungszeitpunkt, buchendes Gerät).
  - Sitzungen: UUID vom Kindgerät, Kind, Start, Ende (leer, solange sie läuft), Grund für das Ende (Kind / Automatischer Stopp), Markierung „offline erfasst“. Mitternachtsaufteilung wird beim Lesen berechnet.
  - Geräte: ID, Name, Rolle, Kind (nur bei Kindgerät), Hash des Tokens, Kopplungsdatum, zuletzt gesehen, Push-Endpoint und WebPush-Schlüssel, entkoppelt-Markierung. Höchstens ein aktives Kindgerät pro Kind.
  - Kopplungscodes: Hash, Rolle, Kind bzw. Name eines neuen Kindes, Ablauf (10 min), eingelöst-Markierung.
  - Checklisten-Stand pro Kindgerät: letzte Liste der fehlenden Punkte, ohne Verlauf.
  - Familien-Zeitzone kommt aus der Konfiguration, nicht aus der DB.
- **Zeitgesteuerte Ereignisse im Prozess:** Der Server berechnet mit `domain` pro Kind den nächsten fälligen Zeitpunkt (Restzeit 0, Automatischer Stopp, Mitternacht) und plant ihn im Prozess. Bei jeder Änderung und beim Neustart wird aus der DB neu gerechnet. Keine Job-Queue, kein Cron, keine gespeicherten Planungen.
- **Automatischer Stopp am Server:** setzt das Ende der Sitzung auf den Zeitpunkt, an dem die Überziehungsgrenze erreicht wurde, mit Grund „Automatischer Stopp“, benachrichtigt die Eltern und schickt dem Kindgerät ein Resync-Signal.
- **Eltern-Benachrichtigungen (alle vom Server, an alle Elterngeräte, Kindname vorne):**
  - Alarm: einmal, wenn die Restzeit einer laufenden Sitzung 0 erreicht, auch wenn das Tablet offline ist. Erfährt der Server eine laufende Sitzung erst beim Sync und ist sie schon in der Überziehung, kommt sie jetzt, einmal.
  - Automatischer Stopp.
  - Checkliste: genau eine Benachrichtigung pro Übergang von „vollständig“ zu „etwas fehlt“, mit den fehlenden Punkten. Die nächste ist erst nach einer vollständigen Meldung möglich.
  - Keine nachgeholten Benachrichtigungen für Sitzungen, die offline schon beendet wurden.
- **Resync-Signale:** an das Kindgerät nach jeder Eltern-Aktion, die sein Kind betrifft, und nach einem Automatischen Stopp; an die Elterngeräte nach Änderungen am Stand eines Kindes (Start, Stopp, Checkliste).
- **Push über ntfy:** Payload Ende-zu-Ende verschlüsselt nach WebPush (RFC 8291) mit den Schlüsseln, die das Gerät bei der Registrierung liefert; enthält den anzuzeigenden Text und den nötigen Stand, damit die Benachrichtigung auch ohne eigenen Netzzugriff der App im Doze erscheint. Zufällige Topics. Der Server publisht intern an ntfy.
- **Sicherheit:**
  - Geräte-Token: zufällig, 256 Bit, Bearer-Header, am Server nur als Hash gespeichert, am Gerät im Android Keystore. Kein Ablauf, gültig bis zum Entkoppeln.
  - Rollenprüfung bei jedem Endpunkt. Ein Kindgerät darf nur die Sitzungen seines Kindes upserten, den eigenen Stand lesen und seine Push-Registrierung aktualisieren.
  - Globales Rate-Limit (nicht pro IP, z. B. 10 Fehlversuche pro Minute) für das Einlösen von Kopplungscodes und das Admin-Passwort.
  - Das letzte Elterngerät kann nicht entkoppelt werden. Das Koppeln eines neuen Kindgeräts entkoppelt das alte automatisch.
- **Admin-Webformular:** serverseitig gerendert (kotlinx.html), ohne JS, auf einem **eigenen Port**, der nur als Host-Port publiziert ist und keine Traefik-Route hat. Es verlangt das Admin-Passwort und kann nur eines: Kopplungscodes für Elterngeräte erzeugen (Bootstrap und Recovery).
- **Konfiguration über Umgebungsvariablen:** Admin-Passwort, Familien-Zeitzone, öffentliche Server-URL, öffentliche und interne ntfy-URL. Fehlt ein Pflichtwert, startet der Server nicht. Die Familien-Zeitzone ist unabhängig von der Container-Zeitzone.

### App

- **Bibliotheken:** Room (lokaler Stand und Outbox), Ktor Client + kotlinx.serialization, Coroutines/Flow, WorkManager (Outbox-Wiederholung), UnifiedPush-Connector, manuelle Dependency Injection (kein Hilt).
- **Alarmkern (Kindgerät):** plant Vorwarnung, Alarm, Alarmwiederholung und Automatischen Stopp mit `AlarmManager.setAlarmClock` und der Berechtigung `USE_EXACT_ALARM`, jeweils aus `domain` und dem lokalen Stand. Er storniert und plant neu bei jedem Start, Stopp und Sync. Beim Klingeln: kurzer Foreground Service (Typ `systemExempted`), Ton über den Alarm-Stream, Full-Screen-Intent (`USE_FULL_SCREEN_INTENT`). Ein Boot-Receiver plant nach einem Neustart neu. „Ton aus“ im Vollbild beendet nur das aktuelle Klingeln, die nächste Wiederholung bleibt geplant.
- **Einrichtungs-Checkliste (Kindgerät):** prüft bei jedem App-Start und bei jedem Sync Exakte Alarme, Full-Screen-Intent, Benachrichtigungen, Akku „Nicht eingeschränkt“, Alarmlautstärke > 0 und den verbundenen UnifiedPush-Verteiler. Fehlendes rot mit direktem Link in die Systemeinstellung. Nicht Prüfbares („Nie schlafende Apps“, Alarme bei „Nicht stören“) als Hinweis zum Abhaken. Die Liste der fehlenden prüfbaren Punkte geht bei jedem Sync an den Server.
- **UnifiedPush-Verteiler:** die ntfy-Android-App auf allen Geräten, eingerichtet auf den Homelab-ntfy. Die App zeigt einen Einrichtungshinweis, wenn kein Verteiler verbunden ist.
- **UI Kindgerät (Variante A „Ring“):** App-Leiste mit Begrüßung und Symbol zur Checkliste; Fortschrittsring mit Restzeit als `mm:ss` und Zustandswort (übrig / läuft / gleich vorbei / drüber); darunter Tageskontingent mit Aufschlüsselung und heute genutzte Zeit; unten breiter Start/Stopp-Knopf in Zustandsfarbe. Bei Überziehung roter pulsierender Ring, Anzeige zählt mit „+“ hoch. Bei Restzeit ≤ 0 ohne Sitzung Knopf deaktiviert („Keine Zeit mehr übrig“). Rotes Banner bei fehlender Checkliste. Offline-Hinweis „gerechnet mit dem Stand von …“. Alarm als Vollbild („Zeit ist um!“, Überziehung, Knopf „Stopp“, „Ton aus · klingelt in 5 min wieder“). Vorwarnung als Ton plus Snackbar. Keine Einstellungen sichtbar.
- **UI Elterngerät (Variante B „Ein Bildschirm“):** eine scrollende Startseite mit Status-Karte (Restzeit, Fortschrittsbalken, „Sitzung läuft seit …“, Aufschlüsselung, „Stand bekannt seit …“, rote fehlende Checklisten-Punkte), „Heute anpassen“ mit Stepper (±5 min), Vorschau, „Buchen“ und „letzte zurücknehmen“; Zeile „Wochenvorlage“ mit Vollbild-Dialog; letzte Tage des Verlaufs mit Link zum ganzen Verlauf. Im ⋮-Menü „Einstellungen & Geräte“: Regeln (Vorwarnschwelle, Überziehungsgrenze, Name des Kindes) und Geräte (Liste, Entkoppeln, Koppeln). Offline: Leiste „Offline · nur Ansicht“, alle Aktionen deaktiviert. In v1 genau ein Kind, keine Kind-Auswahl.
- **Koppeln-Ablauf:** Rolle wählen (Kindgerät für Kind X mit Ersetzen-Hinweis / Elterngerät; gibt es noch kein Kind, zuerst Namen eingeben), dann QR-Code, Code zum Abtippen und Countdown. Am neuen Gerät: QR scannen oder Code plus Server-URL eintippen.
- **Verlauf:** pro Tag „genutzt / Kontingent“ mit Überziehungs-Markierung, darunter die Sitzungen (Markierungen „Automatisch gestoppt“ und „offline erfasst“) und die Tagesanpassungen.
- **Zustandsfarben:** bereit = Primärfarbe, läuft = Grün, Vorwarnung = Bernstein, Überziehung = Rot, aufgebraucht = Grau, jeweils für hell und dunkel. Alles andere aus Material 3 / Dynamic Color.
- **Debug-Build:** versteckter Knopf „Testsitzung mit 2 min Kontingent“.

### Build, Release und Betrieb

- **CI:** GitHub Actions in einem öffentlichen Repo. Gate bei jedem Push auf `main`, jedem PR und jedem Release: alle JVM-Tests (`domain`, `api`, `server`, App-Unit-/Robolectric-Tests) plus Android Lint.
- **Release:** ein gepushter Tag `vX.Y.Z` führt das Gate aus, baut die signierte APK, erstellt ein GitHub Release mit der APK als Asset (automatische Release-Notes) und baut und pusht das Server-Image `ghcr.io/drusin/kid-screen-time-server:X.Y.Z` (`linux/amd64`, öffentlich, kein `latest`). Kein Pre-Release-Kanal.
- **Versionierung:** der Tag ist die einzige Quelle; `versionName` = Tag, `versionCode = X*10000 + Y*100 + Z`; lokal ohne Tag `0.0.0-dev`.
- **Signieren:** ein Release-Keystore (RSA 4096, 25+ Jahre), im Passwortmanager plus Offline-Backup, in CI als Secret, das nur der Tag-Workflow liest. Nie im Repo.
- **Verteilung:** GitHub Releases + Obtainium. Vor dem ersten Einsatz Registrierung des Package-Namens im kostenlosen „limited distribution“-Konto der Android Developer Console mit dem Release-Schlüssel (Checkliste für den Nutzer bei der Umsetzung).
- **Homelab:** Das Repo liefert eine Beispiel-Compose-Konfiguration (Server + ntfy) passend zum bestehenden Homelab: Netz `proxy` plus internes Netz, Subdomains `kidscreentime.rusin.xyz` und `ntfy.rusin.xyz` über die Traefik-`defaultRule`, Admin-Port nur als Host-Port, Bind-Mounts unter dem Homelab-Datenpfad, `user: 1000:1000`, `restart: unless-stopped`, Admin-Passwort in `.env`. ntfy: Traefik-Route nur für `GET`/`HEAD`/`OPTIONS`, keine Benutzer, `cache-duration: 12h`, keine Attachments, kein Upstream, Standard-Rate-Limits.
- **Updates:** manuell, zuerst der Server, dann die App.

## Testentscheidungen

### Was einen guten Test ausmacht

- Getestet wird **äußeres Verhalten an einer Schnittstelle**, nicht die Umsetzung dahinter: Eingaben rein, beobachtbares Ergebnis raus. Keine Tests auf private Funktionen, Zwischenzustände oder Aufrufreihenfolgen von Mocks.
- **Zeit wird vorgestellt, nicht abgewartet.** Jeder Test steuert Wanduhr und monotone Uhr über die injizierten Uhren.
- **Keine Datenbank-Mocks.** Der Server läuft im Test gegen eine echte SQLite-Datei im Temp-Ordner.
- Testnamen beschreiben die Regel in Domänensprache (z. B. „Tagesanpassung bei laufender Sitzung unter Verbrauch löst sofort den Alarm aus“).

### Test-Seams (mit dem Nutzer in Ticket 14 festgelegt)

Möglichst hohe Seams, möglichst wenige:

1. **`domain`-Schnittstelle** (Stand + Zeitpunkt → abgeleitete Werte und nächste fällige Zeitpunkte). Hier liegt der Großteil der Regel-Tests: Tageskontingent aus Vorlage-Version zu Tagesbeginn, Tagesanpassungen und Gegenbuchung, Untergrenze 0, Wochenvorlage ab morgen, Vorwarnung (einmal je Ende des Kontingents, wieder nach Bonus, sofort unter Schwelle), Alarm und 5-Minuten-Wiederholung, Neuberechnung nach Anpassung (Alarm verstummt bei Restzeit > 0), Automatischer Stopp an der Überziehungsgrenze, Mitternachtsaufteilung, Sommerzeit-Grenzen (23-/25-Stunden-Tage, Sitzung über die Umstellung), Startverbot bei Restzeit ≤ 0, Verlauf.
2. **Server-HTTP-API** über Ktor `testApplication` mit echter SQLite, injizierter Uhr und einem Fake-HTTP-Endpunkt anstelle von ntfy. Abgedeckt: Kopplung (Einmaligkeit, 10-min-Ablauf, Rate-Limit, Ersetzen des Kindgeräts, letztes Elterngerät), Rollenprüfung pro Endpunkt, `401` nach Entkoppeln, Snapshots, Eltern-Aktionen, zeitgesteuerte Ereignisse (Uhr vorstellen → Benachrichtigung an ntfy, Automatischer Stopp in der DB), Neuberechnung nach Neustart, Checklisten-Übergänge mit genau einer Benachrichtigung, richtige verschlüsselte Nachricht an ntfy, Admin-Formular nur auf dem Admin-Port, Start-Abbruch bei fehlender Konfiguration, Migrationen.
3. **Sync-Szenarien:** die Outbox-/Sync-Logik der App gegen den echten Server im Testhost. Abgedeckt: Offline-Start und späterer Sync (Alter → Zeitpunkt, „offline erfasst“), doppelte Batches, das frühere Ende gewinnt nach Automatischem Stopp, Zeitpunkte in der Zukunft und Überlappungen werden begrenzt, Neustart zwischen Zeitpunkt und Sync, Tagesanpassung während Offline-Phase führt nach Sync zu sofortigem Alarm, ersetztes Kindgerät bekommt `401` und verwirft die Outbox, neues Kindgerät übernimmt die laufende Sitzung, nachträglich bekannte laufende Sitzung in Überziehung löst genau eine Alarm-Benachrichtigung aus.
4. **App-Alarmkern und ViewModels** mit Robolectric: Der Alarmkern plant oder storniert je nach Zustand die richtigen `setAlarmClock`-Zeitpunkte (Start, Stopp, Sync mit geänderter Restzeit, Mitternacht, Boot, Automatischer Stopp lokal). Ein empfangenes Push-Signal löst einen Sync aus. ViewModel-Tests für Kind- und Eltern-Bildschirm (Zustände, deaktivierte Aktionen offline, Stepper-Vorschau). Keine Compose-UI-Tests.

Dazu ein **Architekturtest**, der direkte Systemuhr-Aufrufe im Produktivcode verbietet.

### Manuell

- **Alarm-Checkliste am echten Samsung-Tablet** (als Dokument im Repo): Bildschirm aus + erzwungener Doze, fremde App im Vordergrund (Vollbild + Alarm-Stream), „Nicht stören“ an, Wiederholung nach 5 min, Vorwarnung, Automatischer Stopp, Neustart während einer Sitzung, Push-Weg (Tagesanpassung am Elterngerät kommt in wenigen Sekunden am Tablet an). Durchgang nur bei relevanten Änderungen (Alarmkern, Manifest/Berechtigungen, `targetSdk`, großes One-UI-/Android-Update), der Push-Weg zusätzlich kurz nach jedem Server-Update.
- **Keine instrumentierten Tests und keine Emulator-Läufe in v1.**

### Prior Art

Im Repo gibt es noch keinen Code und keine Tests. Die Tests dieser Spezifikation bilden die Vorlage für alles Weitere. Erste Tests sollten an Seam 1 (`domain`) entstehen, weil dort die riskantesten Regeln liegen (Sommerzeit, Mitternacht).

## Außerhalb des Umfangs

- Sperren des Geräts oder Blockieren von Apps (Kiosk, Device Owner).
- Veröffentlichung im Play Store, iOS-Unterstützung.
- Automatische Erkennung der Nutzung. Bildschirmzeit wird nur per Sitzung erfasst, das Tablet misst nichts.
- Statistiken und Diagramme.
- Nachträgliches Bearbeiten, Löschen oder Nachtragen von Sitzungen durch die Eltern.
- Tagesanpassungen für künftige Tage. Übertrag von Restzeit auf den nächsten Tag.
- Eltern starten oder stoppen Sitzungen.
- Mehrkind-UI (Kind-Auswahl, weiteres Kind anlegen) und Entfernen von Kindern. Datenmodell und API sind vorbereitet.
- Backups der Server-Datenbank.
- Erkennung von Funkstille des Kindgeräts (Schwellen, Benachrichtigung, „aus“ vs. „gelöscht“). Es gibt nur „zuletzt gesehen“.
- Benutzerkonten, Admin-PIN in der App, VPN, mTLS, IP-Allowlists.
- In-App-Updater, automatische Server-Updates.
- Andere Sprachen als Deutsch.
- Einstellbare Alarmwiederholung (fest 5 min).
- Eine Zeitzone pro Gerät oder Reisen.

## Weitere Hinweise

- **Bei der Umsetzung zu prüfen:**
  - ob die aktuelle UnifiedPush-Android-Bibliothek WebPush-Verschlüsselung (RFC 8291) unterstützt;
  - dass die ntfy-App und die UnifiedPush-Registrierung keine schreibenden Requests nach außen brauchen (sonst passt die nur-lesende Traefik-Route nicht);
  - Full-Screen-Intent-Berechtigung bei Sideloading und „Nicht stören“-Verhalten am echten Tablet;
  - wie der Server nach einem Neustart verhindert, dass eine schon verschickte Alarm- oder Stopp-Benachrichtigung doppelt kommt, ohne Planungen zu speichern (z. B. aus dem Stand der Sitzung ableiten oder pro Sitzung „Alarm gemeldet“ vermerken). Die Spezifikation verlangt nur: einmal pro Sitzung, keine Duplikate.
- **Reihenfolge der Umsetzung (Vorschlag):** `domain` mit Tests → `api` + `server` mit Kopplung, Snapshots, Eltern-Aktionen → Sync-Protokoll → App Kindgerät mit Alarmkern und Checkliste → App Elterngerät → Push (ntfy, WebPush) und Benachrichtigungen → CI/Release und Beispiel-Compose → manuelle Alarm-Checkliste am Tablet.
- **Erster Release:** Vor der Installation auf einem Familiengerät müssen Keystore, Limited-distribution-Konto und Package-Registrierung erledigt sein. Samsung Auto Blocker muss auf dem Tablet aus sein.
- Hintergrund und Begründungen zu jeder Entscheidung stehen in den Tickets 01–14 der Map.
