# 06: Vorwarnung und Alarm am Kindgerät

**What to build:** Während einer Sitzung kommt bei Erreichen der Vorwarnschwelle ein kurzer Ton mit Snackbar. Bei Restzeit 0 klingelt ein Vollbild-Alarm („Zeit ist um!“, Überziehung, Knopf „Stopp“, „Ton aus · klingelt in 5 min wieder“), auch im Standby/Doze, bei „Nicht stören“ und über anderen Apps. Er wiederholt sich alle 5 Minuten, bis das Kind stoppt. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Modul `domain`“ (nächste fällige Zeitpunkte) und „Alarmkern“.

**Blocked by:** 05 Sitzung starten und stoppen

**Status:** ready-for-agent

- [ ] `domain` liefert die nächsten fälligen Zeitpunkte. Die Vorwarnung kommt höchstens einmal pro Sitzung, sofort, wenn die Restzeit schon unter der Schwelle liegt. Der Alarm kommt bei Restzeit 0 (sofort, wenn ≤ 0) und wiederholt sich fest alle 5 Minuten.
- [ ] Der Alarmkern plant mit `AlarmManager.setAlarmClock` und `USE_EXACT_ALARM`. Bei jedem Start, Stopp und Sync storniert er und plant neu.
- [ ] Beim Klingeln läuft ein kurzer Foreground Service (Typ `systemExempted`), der Ton kommt über den Alarm-Stream, dazu ein Full-Screen-Intent.
- [ ] „Stopp“ im Vollbild beendet die Sitzung über die Outbox. „Ton aus“ beendet nur das aktuelle Klingeln, die nächste Wiederholung bleibt geplant.
- [ ] Ein Boot-Receiver plant nach einem Neustart neu.
- [ ] Ist die Restzeit nach einem Sync ≤ 0, klingelt der Alarm sofort.
- [ ] Der Debug-Build hat einen versteckten Knopf „Testsitzung mit 2 min Kontingent“.
- [ ] Robolectric-Tests prüfen, dass der Alarmkern bei Start, Stopp, Sync mit geänderter Restzeit und Boot die richtigen Zeitpunkte plant oder storniert.
- [ ] Die manuelle Alarm-Checkliste liegt als Dokument im Repo (Punkte siehe Spezifikation v1, „Manuell“).
