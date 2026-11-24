# 02: `domain`: Tageskontingent und Restzeit

**What to build:** Die tiefe `domain`-Schnittstelle für ihren ersten Teil. Sie bekommt den Stand eines Kindes und einen Zeitpunkt und liefert den Tag in der Familien-Zeitzone, das Tageskontingent mit Aufschlüsselung, den heutigen Verbrauch, Restzeit, Überziehung, Zustand (bereit / läuft / Vorwarnung / Überziehung / aufgebraucht) und ob ein Start erlaubt ist. Kein I/O, keine eigene Uhr. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Modul `domain`“.

**Blocked by:** 01 Gerüst und CI-Gate

**Status:** ready-for-agent

- [ ] Das Tageskontingent ist der Wert der Wochenvorlage-Version, die um 00:00 des Tages galt, plus die Summe der Tagesanpassungen dieses Tages, mindestens 0.
- [ ] Eine Vorlagen-Version mit „gültig ab morgen“ ändert das heutige Tageskontingent nicht.
- [ ] Mehrere Tagesanpassungen addieren sich. Eine Gegenbuchung hebt eine Anpassung auf.
- [ ] Verbrauch, Restzeit und Überziehung werden aus abgeschlossenen und laufenden Sitzungen in tatsächlich verstrichenen Minuten berechnet.
- [ ] Ein Start ist nur bei Restzeit > 0 erlaubt.
- [ ] Der Zustand ist für jeden Fall korrekt, einschließlich Vorwarnung anhand der Vorwarnschwelle.
- [ ] Die Testnamen beschreiben die Regeln in Domänensprache, und Tests laufen nur über die öffentliche Schnittstelle.
