# 10: Wochenvorlage und Regeln

**What to build:** Auf der Eltern-Startseite öffnet die Zeile „Wochenvorlage“ einen Vollbild-Dialog mit Mo–So. Eine Änderung gilt ab morgen. Im ⋮-Menü „Einstellungen & Geräte“ stellt der Elternteil Vorwarnschwelle, Überziehungsgrenze und den Namen des Kindes ein. Das Tablet übernimmt alles nach dem nächsten Sync. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Eltern: Wochenvorlage und Regeln“.

**Blocked by:** 06 Vorwarnung und Alarm am Kindgerät

**Status:** ready-for-agent

- [ ] Wochenvorlage setzen legt eine neue Version mit „gültig ab morgen“ an. Es gibt keinen Mitternachts-Job.
- [ ] Das heutige Tageskontingent bleibt nach einer Vorlagen-Änderung unverändert, morgen gilt der neue Wert.
- [ ] Regeln setzen ändert Vorwarnschwelle, Überziehungsgrenze und Namen. Der Alarmkern am Tablet plant nach dem Sync mit den neuen Werten.
- [ ] Beide Aktionen liefern den neuen Eltern-Snapshot.
- [ ] Am Kindgerät gibt es weiterhin keine Einstellungen.
- [ ] Ein Server-API-Test deckt Vorlage ab morgen, Regeln und die Rollenprüfung ab.
