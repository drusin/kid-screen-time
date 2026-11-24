# 08: Offline-Start und -Stopp robust

**What to build:** Ohne WLAN startet und stoppt das Kind genauso. Das Tablet zeigt „gerechnet mit dem Stand von …“. Sobald wieder Netz da ist, landen die Sitzungen korrekt am Server, und die Eltern sehen sie als „offline erfasst“. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Sync-Protokoll“.

**Blocked by:** 05 Sitzung starten und stoppen

**Status:** done

- [x] Scheitert ein Sync, wiederholt ihn ein WorkManager-Job mit Netzwerk-Bedingung. Es gibt keinen separaten Online-Pfad und keinen periodischen Job.
- [x] Der Batch enthält alle offenen Sitzungen in Startreihenfolge.
- [x] Erfährt der Server einen Start erst mit Verzögerung, markiert er die Sitzung als „offline erfasst“.
- [x] Liegt ein Neustart zwischen Zeitpunkt und Sync, schickt das Gerät seine Wanduhrzeit, korrigiert um den Versatz aus dem letzten Snapshot.
- [x] Der Server begrenzt jeden Zeitpunkt auf [Ende der vorigen Sitzung, jetzt]. Zukünftige Zeitpunkte und Überlappungen werden gekappt.
- [x] Doppelte Batches sind wirkungslos.
- [x] Offline zeigt das Kindgerät den Hinweis „gerechnet mit dem Stand von …“.
- [x] Gesynct wird auch beim Entsperren des Geräts.
- [x] Sync-Szenario-Tests decken ab: Offline-Start mit späterem Sync, doppelte Batches, Begrenzung, Neustart zwischen Zeitpunkt und Sync.

## Notizen zur Umsetzung

**Server (`ChildService.upsert`, keine Migration):** `sessions.recorded_offline` gab es schon seit Migration 002, `Database.MIGRATIONS` bleibt bei zwei Einträgen. Ein neuer Start wird „offline erfasst“, wenn sein Alter beim Empfang größer als 1 min ist (ein langsamer Request ist es nicht). Der Start einer neuen Sitzung wird auf das Ende der letzten beendeten Sitzung des Kindes gekappt, die vor diesem Start begann (der Batch läuft in Startreihenfolge); das Ende nie vor den Start und nie nach „jetzt“ (Alter < 0 zählt als 0). Neues optionales Feld `SessionRecord.recordedOffline` im Kind-Snapshot (für den Verlauf, Ticket 16); die Eltern-Oberfläche zeigt es noch nicht.

**Korrektur zu Ticket 07, „das frühere Ende gewinnt“:** gemeint ist das zeitlich frühere Ende. Ein späteres Ende ändert nie etwas; ein Ende, das mehr als 1 s früher liegt als das gespeicherte, ersetzt es, der Grund wird `STOPPED` (auch nach `AUTOMATIC_STOP`). Die 1 s Toleranz verhindern, dass wiederholte Batches (das Alter wird jedes Mal neu gemessen) oder das Tablet-eigene Stopp-Ende, das dem Automatischen Stopp des Servers gleicht, den Grund kippen. Am Gerät gilt dasselbe (`ChildSnapshot.toKindStand(pending)`): ein früheres Ende aus der Outbox ersetzt das Ende des Snapshots. Die Tests aus 07 schrieben nur „späteres Ende ändert nichts“ fest und blieben unverändert gültig.

**App:** `SyncRetry` (`sync/SyncRetry.kt`): `ChildRepository.sync()` fordert nach jedem Fehlschlag (Netz, kein `200`/`401`) die Wiederholung an; `WorkManagerSyncRetry` plant eine eindeutige Arbeit (`KEEP`, `CONNECTED`, exponentieller Backoff ab 30 s), `SyncRetryWorker` ruft `sync()` und antwortet bei `Unavailable` mit `retry()`. `UnlockSyncReceiver` (`USER_PRESENT`, dynamisch in `KidScreenTimeApp.onCreate`) synct beim Entsperren; stirbt der Prozess, wirkt das nächste Alarm-/Push-/Boot-/App-Start-Ereignis. Neustart-Erkennung: weichen Alter nach monotoner Uhr und nach Wanduhr um mehr als 2 min ab oder ist das monotone Alter negativ, zählt die Wanduhr (je Zeitpunkt einzeln, ein Start vor und ein Stopp nach dem Neustart gehen also beide richtig). Der „Versatz aus dem letzten Snapshot“ muss nicht eigens angewendet werden: das Alter ist eine Differenz zweier Werte derselben Wanduhr, der Versatz zum Server kürzt sich heraus, und der Server rechnet weiter „Empfangszeit − Alter“. Ohne Room-Änderung, weil kein Boot-Zähler gespeichert wird (Heuristik statt Schema). Einschränkung: springt die Wanduhr ohne Neustart um mehr als 2 min, wird die Wanduhr genommen (selten, und dann ist sie ohnehin die Frage).

**Hinweis „gerechnet mit dem Stand von …“:** gab es seit Ticket 04/05 (`child_offline_notice`, `ChildHomeUiState.offline`/`snapshotAt`, Test in `ChildHomeViewModelTest`); unverändert.

**Tests:** `ChildSyncApiTest` (Server, Offline-Markierung, Kappen, früheres Ende, Toleranz, Wiederholung), `OfflineSyncScenarioTest` (Offline-Start mit späterem Sync, Reihenfolge im Batch, doppelter Batch nach verlorener Antwort, Kappen, Zukunft, drei Neustart-Fälle, Kind stoppt offline vor Automatischem Stopp des Servers), `SyncRetryTest` (echter WorkManager mit `work-testing`, Entsperren), `ChildCalculationTest`. Neu: `ManualClock.reboot`, `work-runtime`/`work-testing` in `gradle/libs.versions.toml`.

**Manuell am Gerät:** WLAN aus, Start, Neustart des Tablets, WLAN an: der Job läuft ohne App-Start, die Sitzung erscheint am Server mit richtigem Start (Beispiel in `docs/alarm-checkliste.md` ergänzt).
