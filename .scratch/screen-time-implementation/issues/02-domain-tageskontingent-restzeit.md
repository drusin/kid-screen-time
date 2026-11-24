# 02: `domain`: Tageskontingent und Restzeit

**What to build:** Die tiefe `domain`-Schnittstelle für ihren ersten Teil. Sie bekommt den Stand eines Kindes und einen Zeitpunkt und liefert den Tag in der Familien-Zeitzone, das Tageskontingent mit Aufschlüsselung, den heutigen Verbrauch, Restzeit, Überziehung, Zustand (bereit / läuft / Vorwarnung / Überziehung / aufgebraucht) und ob ein Start erlaubt ist. Kein I/O, keine eigene Uhr. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Modul `domain`“.

**Blocked by:** 01 Gerüst und CI-Gate

**Status:** done

- [x] Das Tageskontingent ist der Wert der Wochenvorlage-Version, die um 00:00 des Tages galt, plus die Summe der Tagesanpassungen dieses Tages, mindestens 0.
- [x] Eine Vorlagen-Version mit „gültig ab morgen“ ändert das heutige Tageskontingent nicht.
- [x] Mehrere Tagesanpassungen addieren sich. Eine Gegenbuchung hebt eine Anpassung auf.
- [x] Verbrauch, Restzeit und Überziehung werden aus abgeschlossenen und laufenden Sitzungen in tatsächlich verstrichenen Minuten berechnet.
- [x] Ein Start ist nur bei Restzeit > 0 erlaubt.
- [x] Der Zustand ist für jeden Fall korrekt, einschließlich Vorwarnung anhand der Vorwarnschwelle.
- [x] Die Testnamen beschreiben die Regeln in Domänensprache, und Tests laufen nur über die öffentliche Schnittstelle.

## Notizen zur Umsetzung

Paket `io.github.drusin.kidscreentime.domain.bildschirmzeit` (Modul `domain`, Tests in `TageskontingentUndRestzeitTest`).

**Schnittstelle:** `KindStand(zeitzone, wochenvorlage, tagesanpassungen, sitzungen, vorwarnschwelle = 5 min, ueberziehungsgrenze = 60 min).tagesstand(jetzt: Instant): Tagesstand`. Reine Daten, keine Uhr. Aufrufer übergeben `WallClock.now()` oder einen beliebigen Zeitpunkt (Verlauf).

- Eingaben: `Wochenvorlage(versionen)` mit `WochenvorlageVersion(gueltigAb, minuten je Wochentag)`, `Tagesanpassung(tag, deltaMinuten, gebuchtAm)`, `Sitzung(id, start, ende: SitzungsEnde?)` mit `SitzungsEnde(zeitpunkt, grund: Endegrund)`, `Endegrund { GESTOPPT, AUTOMATISCHER_STOPP }`.
- Ausgabe `Tagesstand`: `tag`, `tagesende` (nächste Mitternacht), `tageskontingent` (`vorlage`, `anpassungen`, `gesamt`), `verbrauch`, `restzeit`, `ueberziehung`, `zustand` (`BEREIT, LAEUFT, VORWARNUNG, UEBERZIEHUNG, AUFGEBRAUCHT`), `laufendeSitzung`, `startErlaubt`.

**Entscheidungen:**
- `restzeit` und `ueberziehung` sind auf >= 0 begrenzt. Fällige Zeitpunkte (Tickets 06/07) lassen sich daraus ableiten: Alarm bei `jetzt + restzeit`, Automatischer Stopp bei `jetzt + restzeit + (ueberziehungsgrenze - ueberziehung)`, nächste Mitternacht ist `tagesende`. Sie sind noch nicht gebaut und kommen als weitere Funktionen auf `KindStand`/`Tagesstand`.
- Der Verbrauch zählt nur den Teil jeder Sitzung zwischen Tagesbeginn und `jetzt`. Dadurch teilen sich Sitzungen über Mitternacht und Zeitumstellungstage von selbst richtig auf (Tagesgrenzen über `ZoneId`). Ticket 11 ergänzt die Tests dazu, Ticket 16 die Aufteilung für den Verlauf.
- `Tagesanpassung.gebuchtAm`: Buchungen nach `jetzt` zählen nicht, so ist der Stand zu einem früheren Zeitpunkt reproduzierbar.
- Vor der ersten Wochenvorlage-Version ist das Kontingent 0.
- Zustand: laufende Sitzung = `start <= jetzt < ende` (oder offen). `VORWARNUNG` gilt bei `0 < restzeit <= vorwarnschwelle`, `UEBERZIEHUNG` bei laufender Sitzung und Restzeit 0, `AUFGEBRAUCHT` bei Restzeit 0 ohne laufende Sitzung. „Höchstens einmal pro Sitzung“ gehört zu Ticket 06.
- `startErlaubt` ist zusätzlich false, solange eine Sitzung läuft.
- Beträge: Eingaben der Eltern (Vorlage, Anpassung) in Minuten (`Int`), Schwellen und alle Ergebnisse als `java.time.Duration`.
- Bezeichner sind deutsche Fachbegriffe aus dem Glossar in ASCII-Schreibung (`ueberziehung`).

**Abweichungen:** keine. Der Worktree startete vor dem Gerüst-Commit und wurde per Fast-Forward auf `v1-implementation` gebracht.
