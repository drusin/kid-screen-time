# Glossar

**Kind**: Die Person, deren Bildschirmzeit begrenzt wird. Ein Kind hat einen Namen. Wochenvorlage, Tagesanpassungen, Vorwarnschwelle, Überziehungsgrenze, Verlauf und Kindgerät gehören jeweils zu einem Kind. Aktuell gibt es eins, weitere sollen später möglich sein. Ein Kind wird angelegt, wenn sein erstes Kindgerät gekoppelt wird.

**Eltern**: Die Personen, die Kontingente festlegen und ändern.

**Bildschirmzeit**: Jede Zeit vor einem Bildschirm (Tablet, Konsole, Fernseher, …). Sie wird nicht pro Gerät erfasst, sondern als eine Summe.

**Tag**: Ein Kalendertag von 00:00 bis 24:00 in der Zeitzone der Familie. Dauern zählen als tatsächlich verstrichene Minuten, auch an Tagen mit Zeitumstellung.

**Tageskontingent**: Die Bildschirmzeit, die dem Kind an einem Tag zusteht: der Wert der Wochenvorlage für diesen Wochentag, wie er zu Tagesbeginn galt, plus die Summe der Tagesanpassungen dieses Tages. Es ist nie kleiner als 0.

**Wochenvorlage**: Ein Standard-Kontingent pro Wochentag (Mo–So). Änderungen gelten ab dem nächsten Tag.

**Tagesanpassung**: Ein Zu- oder Abschlag in Minuten, den die Eltern auf das Tageskontingent des heutigen Tages geben (z. B. „+30 min Bonus“, „−15 min“). Sie wirkt sofort. Mehrere Tagesanpassungen eines Tages addieren sich. Zurückgenommen wird eine Tagesanpassung durch eine Gegenbuchung.
_Avoid_: Tagesausnahme (ersetzt nichts, sondern verändert)

**Restzeit**: Tageskontingent minus die heute schon verbrauchte Bildschirmzeit. Ungenutzte Restzeit verfällt am Tagesende.

**Überziehung**: Bildschirmzeit, die nach Ablauf des Tageskontingents weiter gezählt wird.

**Sitzung**: Ein Timer-Lauf vom Start bis zum Stopp. Nur das Kind startet und stoppt sie, am Kindgerät. Die einzige Ausnahme ist der Automatische Stopp. Sie kann nur bei Restzeit > 0 gestartet werden. Zeit über Mitternacht wird auf beide Tage aufgeteilt und zählt ab 00:00 gegen das neue Tageskontingent. Eine Sitzung, die der Server erst nachträglich erfährt, weil das Kindgerät offline war, ist **offline erfasst** und wird im Verlauf so markiert.

**Automatischer Stopp**: Das Ende einer Sitzung ohne Zutun des Kindes, wenn die Überziehung eine von den Eltern einstellbare Grenze erreicht (Standard: 60 Minuten). Die Eltern werden benachrichtigt.

**Kindgerät**: Das gekoppelte Gerät des Kindes (Tablet). Jedes Kind hat genau ein Kindgerät. Wird ein neues gekoppelt, wird das alte entkoppelt, und eine laufende Sitzung läuft am neuen Gerät weiter. Hier wird die Sitzung gestartet und gestoppt, die Restzeit angezeigt und der Alarm abgespielt.

**Einrichtungs-Checkliste**: Die Liste der Einstellungen am Kindgerät, die für einen zuverlässigen Alarm nötig sind (z. B. Alarmlautstärke über 0). Fehlt ein Punkt, sehen es das Kindgerät und die Eltern, und die Eltern werden einmal benachrichtigt.

**Elterngerät**: Ein gekoppeltes Gerät der Eltern. Hier werden Kontingente bearbeitet und der Verlauf angesehen, und hier kommt die Benachrichtigung beim Alarm an. Ein Elterngerät gehört zu allen Kindern der Familie.

**Vorwarnung**: Ein kurzer Ton am Kindgerät, wenn die Restzeit eine einstellbare Schwelle erreicht oder schon darunter liegt (Standard: 5 Minuten). Sie kommt einmal je Ende des Kontingents: Rückt das Ende durch einen Bonus (oder ein neues Kontingent nach Mitternacht) nach hinten, kommt sie wieder die Schwelle vor dem neuen Ende; ein Abzug löst keine zweite aus.

**Alarm**: Klingeln am Kindgerät, wenn die Restzeit 0 erreicht. Es wiederholt sich alle 5 Minuten, solange die Sitzung läuft und die Restzeit nicht wieder über 0 steigt. Gesperrt wird nichts. Die Eltern bekommen zusätzlich eine Benachrichtigung.

**Kopplung**: Das Verbinden eines Geräts mit dem Server. Dabei wird festgelegt, ob es ein Kindgerät (für welches Kind) oder ein Elterngerät ist. Die Rolle eines Geräts ändert sich danach nicht mehr.

**Kopplungscode**: Ein einmaliger, 10 Minuten gültiger Code (als QR-Code oder zum Abtippen), mit dem ein Gerät gekoppelt wird. Ein Elterngerät erzeugt ihn. Den ersten Code (und den nach Verlust aller Elterngeräte) erzeugen die Eltern direkt am Server im Heimnetz.

**Entkoppeln**: Ein Elterngerät entzieht einem Gerät dauerhaft den Zugang. Das letzte Elterngerät kann nicht entkoppelt werden.
_Avoid_: Löschen, Abmelden

**Verlauf**: Die Liste vergangener Sitzungen und Tagesverbräuche.
