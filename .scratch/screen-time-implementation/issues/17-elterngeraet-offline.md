# 17: Elterngerät offline

**What to build:** Ohne Netz zeigt das Elterngerät den letzten bekannten Stand mit der Leiste „Offline · nur Ansicht“. Alle Aktionen sind deaktiviert. Die Statuskarte zeigt, seit wann der Stand des Kindgeräts bekannt ist. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Eltern: offline“.

**Blocked by:** 09 Tagesanpassung buchen

**Status:** done

- [x] Der Eltern-Snapshot wird lokal gecacht und offline gelesen.
- [x] Offline sind Buchen, Zurücknehmen, Wochenvorlage, Regeln, Koppeln und Entkoppeln deaktiviert.
- [x] Die Statuskarte zeigt „Stand bekannt seit …“ aus dem Snapshot.
- [x] Das Elterngerät gleicht beim App-Start und nach jeder eigenen Aktion ab.
- [x] Ein ViewModel-Test (Robolectric) prüft, dass die Aktionen offline deaktiviert sind.

## Notizen zur Umsetzung

- Cache: Room-Tabelle `parent_snapshot` (eine Zeile, JSON des `ParentSnapshot`, Version 3, `MIGRATION_2_3`, Schema-JSON `3.json`, Schritt in `AppDatabaseMigrationTest`). `ParentRepository` schreibt jeden ankommenden Snapshot (Laden und Antwort jeder Aktion) und liefert bei Netzfehler `SnapshotResult.Cached(snapshot)`, ohne Cache `Unavailable`. Jedes `401` leert den Cache zusammen mit `markRevoked()` (kein Stand der Familie bleibt auf einem entkoppelten Gerät).
- Offline-Zustand: `ParentHomeUiState.offline` (statt `unavailable`) und `canAct = !acting && !offline` als der eine Schalter. `canBook`, `canUnpair`, alle Guards der Aktionen und `runAction` selbst nutzen ihn; die Bildschirme aktivieren ihre Knöpfe, Stepper und Eingaben mit `state.canAct` (Startseite, Wochenvorlage, Einstellungen mit Regeln und Geräten, „Gerät koppeln“-Knopf). Scheitert eine Aktion am Netz (`Unavailable`), wird das Gerät offline; `401` bleibt „Gerät wurde entkoppelt“, nie offline.
- Wieder online: der Abgleich beim App-Start ist `refresh()` in `init`; nach jeder eigenen Aktion ist die Antwort der Abgleich (Muster aus 09/10). Offline gibt es zusätzlich „Erneut versuchen“ in der Leiste und `refreshIfOffline()` bei `ON_RESUME` (`ParentRoot`), plus das Resync-Signal.
- „Stand bekannt seit …“: kein neues Feld nötig. Es ist „zuletzt gesehen“ des Kindgeräts aus der Geräteliste des Eltern-Snapshots (`DeviceInfo.lastSeenEpochMillis`; der Server setzt es bei jedem authentifizierten Aufruf, also auch beim Sync). Ohne Kindgerät oder ohne Kontakt entfällt die Zeile. `ParentHomeUiState.knownSinceEpochMillis`.
- „Gerät koppeln“ lädt den Snapshot selbst; `Cached` zählt dort wie `unavailable` (zeigt die Leiste, kein Code erzeugbar).
- Nicht prüfbar ohne Gerät: Aussehen der Leiste, `ON_RESUME`-Abgleich (siehe manuelle Checkliste unten).

### Manuelle Prüfung am Gerät

- [ ] Flugmodus an, App neu starten: Startseite zeigt den letzten Stand, Leiste „Offline · nur Ansicht“, Buchen, Zurücknehmen, Wochenvorlage speichern, Regeln speichern, Entkoppeln und „Gerät koppeln“ sind deaktiviert.
- [ ] Flugmodus aus, App in den Vordergrund holen oder „Erneut versuchen“: Leiste verschwindet, Aktionen gehen wieder.
