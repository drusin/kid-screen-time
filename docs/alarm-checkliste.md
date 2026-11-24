# Alarm-Checkliste am Samsung-Tablet (manuell)

Es gibt keine instrumentierten Tests und keine Emulator-Läufe. Was nur am Gerät prüfbar ist (Doze, „Nicht stören“, Vollbild über anderen Apps, Neustart), steht hier. Die Robolectric-Tests prüfen nur, dass der Alarmkern die richtigen `setAlarmClock`-Zeitpunkte plant.

**Wann durchgehen:** bei relevanten Änderungen (Alarmkern, Manifest/Berechtigungen, `targetSdk`, großes One-UI-/Android-Update). Der Push-Weg (Punkt 9) zusätzlich kurz nach jedem Server-Update.

## Vorbereitung

- Debug-Build auf das Tablet installieren (Paket `...kidscreentime.debug`, Name „Bildschirmzeit Dev“). Er hat unten auf dem Kind-Bildschirm den kleinen Knopf **„Testsitzung mit 2 min Kontingent“**: ein lokaler Timer mit 2 min Kontingent und 1 min Vorwarnschwelle, ohne Server. So dauert ein Durchgang Minuten statt Stunden.
- Das Tablet ist als Kindgerät gekoppelt (der Kind-Bildschirm ist sichtbar). Der Knopf erscheint, sobald der erste Stand geladen ist.
- Einstellungen am Tablet (später prüft die Einrichtungs-Checkliste das, Ticket 15): Alarme & Erinnerungen erlaubt, „Auf Vollbild-Benachrichtigungen“ erlaubt, Benachrichtigungen erlaubt, Akku „Nicht eingeschränkt“, Alarmlautstärke deutlich über 0. Samsung **Auto Blocker aus**.
- Jeder Punkt: Ergebnis (ok / Abweichung) und Datum unten notieren.

## Punkte

1. **Grundlauf.** Testsitzung starten. Nach 1 min kommt ein kurzer Ton (Vorwarnung), bei geöffneter App zusätzlich eine Snackbar. Nach 2 min klingelt der Alarm im Vollbild („Zeit ist um!“, Überziehung zählt mit).
2. **Stopp.** Im Vollbild „Stopp“: Klingeln endet, Vollbild schließt, kein weiterer Alarm nach 5 min.
3. **Ton aus.** Neuen Test starten, bis der Alarm klingelt, „Ton aus · klingelt in 5 min wieder“: Das Klingeln endet, die Sitzung läuft weiter. Nach genau 5 min klingelt es wieder.
4. **Wiederholung nach 5 min.** Alarm klingeln lassen (nicht bedienen), bis es sich wiederholt: Es klingelt alle 5 min, solange nicht gestoppt wird (der Ton selbst endet nach 60 s).
5. **Bildschirm aus + erzwungener Doze.** Testsitzung starten, Bildschirm ausschalten, `adb shell dumpsys deviceidle force-idle`. Vorwarnung und Alarm kommen pünktlich, der Bildschirm geht an, der Vollbild-Alarm erscheint über dem Sperrbildschirm.
6. **Fremde App im Vordergrund.** Eine andere App (z. B. YouTube) öffnen, dann Testsitzung wie in 1 laufen lassen: Der Vollbild-Alarm erscheint über der fremden App, der Ton läuft über den Alarm-Stream (auch bei Medienlautstärke 0).
7. **„Nicht stören“ an.** Wie 6 mit aktiviertem „Nicht stören“: Alarm und Vorwarnung sind hörbar (Alarme sind erlaubt). Bei Abweichung die Ausnahme „Alarme“ in „Nicht stören“ prüfen.
8. **Neustart während einer Sitzung.** Testsitzung starten, Tablet neu starten (Entsperren ist nicht nötig, der Boot-Receiver braucht nur den Gerätestart; bei Samsung „geschützter Speicher“ ggf. einmal entsperren). Der Alarm kommt trotzdem zur richtigen Zeit. Mit echter Sitzung wiederholen: Start am Kind-Bildschirm, Neustart, Alarm bei Restzeit 0.
9. **Push-Weg** (ab Ticket 13): Tagesanpassung am Elterngerät buchen. Sie kommt in wenigen Sekunden am Tablet an, der Alarmkern plant neu (Bonus bei klingelndem Alarm lässt ihn verstummen, Abzug unter den Verbrauch lässt ihn sofort klingeln).
10. **Automatischer Stopp** (Ticket 07): Sitzung bis zur Überziehungsgrenze laufen lassen (Debug: Testsitzung, 2 min Kontingent plus Überziehungsgrenze). Die Sitzung endet am Tablet ohne Zutun, der Alarm hört auf, das Vollbild schließt. Auch mit ausgeschaltetem WLAN prüfen; danach WLAN an: am Server steht „Automatisch gestoppt“ mit dem Ende zur Grenze.
11. **Nächster Wecker.** In der Uhr-App zeigt das Tablet während einer Sitzung den nächsten geplanten Zeitpunkt als „nächster Wecker“ (so sieht man, dass `setAlarmClock` geplant ist).
12. **Offline und Neustart** (Ticket 08): WLAN aus, am Kind-Bildschirm Start (die Anzeige „Server nicht erreichbar · gerechnet mit dem Stand von …“ erscheint), Tablet neu starten, WLAN an, ohne die App zu öffnen. Der WorkManager-Job liefert die Sitzung: am Server steht der Start zum richtigen Zeitpunkt, markiert als „offline erfasst“. Dasselbe mit Entsperren statt Job: WLAN an, Bildschirm sperren und entsperren, der Sync läuft.

## Durchgänge

| Datum | Gerät / Android | Ergebnis |
|-------|-----------------|----------|
|       |                 |          |
