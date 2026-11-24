# Glossar

**Kind**: Die Person, deren Bildschirmzeit begrenzt wird. Aktuell gibt es eins, weitere sollen später möglich sein.

**Eltern**: Die Personen, die Kontingente festlegen und ändern.

**Bildschirmzeit**: Jede Zeit vor einem Bildschirm (Tablet, Konsole, Fernseher, …). Sie wird nicht pro Gerät erfasst, sondern als eine Summe.

**Tag**: Ein Kalendertag von 00:00 bis 24:00 in der Zeitzone der Familie. Dauern zählen als tatsächlich verstrichene Minuten, auch an Tagen mit Zeitumstellung.

**Tageskontingent**: Die Bildschirmzeit, die dem Kind an einem Tag zusteht: der Wert der Wochenvorlage für diesen Wochentag, wie er zu Tagesbeginn galt, plus die Summe der Tagesanpassungen dieses Tages. Es ist nie kleiner als 0.

**Wochenvorlage**: Ein Standard-Kontingent pro Wochentag (Mo–So). Änderungen gelten ab dem nächsten Tag.

**Tagesanpassung**: Ein Zu- oder Abschlag in Minuten, den die Eltern auf das Tageskontingent des heutigen Tages geben (z. B. „+30 min Bonus“, „−15 min“). Sie wirkt sofort. Mehrere Tagesanpassungen eines Tages addieren sich. Zurückgenommen wird eine Tagesanpassung durch eine Gegenbuchung.
_Avoid_: Tagesausnahme (ersetzt nichts, sondern verändert)

**Restzeit**: Tageskontingent minus die heute schon verbrauchte Bildschirmzeit. Ungenutzte Restzeit verfällt am Tagesende.

**Überziehung**: Bildschirmzeit, die nach Ablauf des Tageskontingents weiter gezählt wird.

**Sitzung**: Ein Timer-Lauf vom Start bis zum Stopp. Nur das Kind startet und stoppt sie, am Kindgerät. Die einzige Ausnahme ist der Automatische Stopp. Sie kann nur bei Restzeit > 0 gestartet werden. Zeit über Mitternacht wird auf beide Tage aufgeteilt und zählt ab 00:00 gegen das neue Tageskontingent.

**Automatischer Stopp**: Das Ende einer Sitzung ohne Zutun des Kindes, wenn die Überziehung eine von den Eltern einstellbare Grenze erreicht (Standard: 60 Minuten). Die Eltern werden benachrichtigt.

**Kindgerät**: Das gekoppelte Gerät des Kindes (Tablet). Hier wird die Sitzung gestartet und gestoppt, die Restzeit angezeigt und der Alarm abgespielt.

**Elterngerät**: Ein gekoppeltes Gerät der Eltern. Hier werden Kontingente bearbeitet und der Verlauf angesehen, und hier kommt die Benachrichtigung beim Alarm an.

**Vorwarnung**: Ein kurzer Ton am Kindgerät, einmal pro Sitzung, wenn die Restzeit eine einstellbare Schwelle erreicht oder schon darunter liegt (Standard: 5 Minuten).

**Alarm**: Klingeln am Kindgerät, wenn die Restzeit 0 erreicht. Es wiederholt sich alle 5 Minuten, solange die Sitzung läuft und die Restzeit nicht wieder über 0 steigt. Gesperrt wird nichts. Die Eltern bekommen zusätzlich eine Benachrichtigung.

**Verlauf**: Die Liste vergangener Sitzungen und Tagesverbräuche.
