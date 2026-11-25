# 10: Wochenvorlage und Regeln

**What to build:** Auf der Eltern-Startseite öffnet die Zeile „Wochenvorlage“ einen Vollbild-Dialog mit Mo–So. Eine Änderung gilt ab morgen. Im ⋮-Menü „Einstellungen & Geräte“ stellt der Elternteil Vorwarnschwelle, Überziehungsgrenze und den Namen des Kindes ein. Das Tablet übernimmt alles nach dem nächsten Sync. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Eltern: Wochenvorlage und Regeln“.

**Blocked by:** 06 Vorwarnung und Alarm am Kindgerät

**Status:** done

- [x] Wochenvorlage setzen legt eine neue Version mit „gültig ab morgen“ an. Es gibt keinen Mitternachts-Job.
- [x] Das heutige Tageskontingent bleibt nach einer Vorlagen-Änderung unverändert, morgen gilt der neue Wert.
- [x] Regeln setzen ändert Vorwarnschwelle, Überziehungsgrenze und Namen. Der Alarmkern am Tablet plant nach dem Sync mit den neuen Werten.
- [x] Beide Aktionen liefern den neuen Eltern-Snapshot.
- [x] Am Kindgerät gibt es weiterhin keine Einstellungen.
- [x] Ein Server-API-Test deckt Vorlage ab morgen, Regeln und die Rollenprüfung ab.

## Notizen zur Umsetzung

- **Endpunkte:** `POST ApiPaths.PARENT_WEEKLY_TEMPLATE` (`WeeklyTemplateRequest(childId, minutes[7] Mo–So)`) und `POST ApiPaths.PARENT_RULES` (`RulesRequest(childId, name, warningThresholdMinutes, overrunLimitMinutes)`), nur Elterngerät, Antwort ist der neue `ParentSnapshot`. `400` bei falscher Anzahl, Werten außerhalb 0 bis 24 h, leerem Namen oder unbekanntem Kind. Beide rufen `notifyStandChanged()` und danach `push.signalChild`.
- **Schema:** keine Migration. Die Regeln liegen schon als Spalten in `children` (Migration 002), die Vorlage in `weekly_template_versions`. Eine neue Vorlage ist eine Zeile mit `valid_from` = morgen (Familien-Zeitzone); eine zweite Änderung am selben Tag ersetzt diese Zeile (`INSERT OR REPLACE`), ältere Versionen bleiben. Kein Mitternachts-Job: `domain` wählt die Version nach Datum. `domain` blieb unverändert.
- **Snapshot:** `ParentChildSnapshot.weeklyTemplateTomorrow` (neu, optional): die Vorlage, die morgen gilt. Der Dialog und die Zeile „Wochenvorlage“ zeigen sie, damit der Elternteil nach dem Speichern seine Änderung sieht. Das Kindgerät hatte `templateTomorrow` schon.
- **Regeln gelten sofort** (nur die Vorlage wartet bis morgen); die Überziehungsgrenze verschiebt den Automatischen Stopp (Test mit Planer), die Vorwarnschwelle plant den Alarmkern am Tablet nach dem Sync neu (`RulesScenarioTest`).
- **App:** Startseite hat die Zeile „Wochenvorlage“ (öffnet `WeeklyTemplateScreen`, Vollbild, Stepper je Wochentag in 5 min) und ein ⋮-Menü „Einstellungen & Geräte“ (`ParentSettingsScreen`: Abschnitt „Regeln“, Abschnitt „Geräte“ mit „Gerät koppeln“; Ticket 12 ergänzt die Geräteliste dort; der frühere „Gerät koppeln“-Knopf in der Titelleiste ist dort hineingewandert). `ParentRoot` schaltet per Flags. `ParentHomeViewModel.saveWeeklyTemplate/saveRules(…, onDone)` laufen durch `runAction`; der Dialog schließt erst bei `Done`, bei Fehlern bleibt die Eingabe stehen. Entwürfe der Bildschirme sind lokaler Compose-Zustand (`rememberSaveable`).
- **Kindgerät:** keine Einstellungen, keine Änderung.
- **Tests:** `WeeklyTemplateAndRulesApiTest` (Server), `ParentHomeViewModelTest` (Erweiterung), `RulesScenarioTest` (App gegen Server).
- **Manuell zu prüfen:** Aussehen von Wochenvorlagen-Dialog, ⋮-Menü und Einstellungen am Gerät (Compose ist nicht per UI-Test abgedeckt).
- **Ticket 13:** Das Sammel-Kriterium bleibt offen, solange Entkoppeln/Ersetzen (12) kein Signal sendet; Vorlage und Regeln sind verdrahtet und getestet.
