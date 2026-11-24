# 05: Sitzung starten und stoppen

**What to build:** Das Kind drückt den großen Knopf, die Sitzung startet, der Ring zählt `mm:ss` herunter, und der Knopf wird zu „Stopp“. Nach dem Stopp bleibt die Restzeit für später. Bei Restzeit 0 ist der Knopf deaktiviert und zeigt „Keine Zeit mehr übrig“. Die Eltern sehen „Sitzung läuft seit …“. Jede Änderung geht zuerst in die Room-Outbox, danach folgt sofort ein Sync. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Sync-Protokoll“.

**Blocked by:** 04 Kindgerät koppeln und Restzeit anzeigen

**Status:** done

- [x] Beim Start erzeugt das Gerät eine Sitzungs-UUID. Jeder Sync überträgt den ganzen Zustand der Sitzung (Start, Ende oder leer), und der Server legt an oder aktualisiert (Upsert).
- [x] Zeitpunkte werden als Alter übertragen, gemessen mit der monotonen Uhr. Der Server rechnet „Serverzeit beim Empfang − Alter“.
- [x] Der Server lehnt keinen Start ab. Das Startverbot prüft nur das Kindgerät.
- [x] Ein Stopp für eine schon beendete Sitzung ändert nichts, denn das frühere Ende gewinnt.
- [x] Die UI zeigt Änderungen sofort aus dem lokalen Stand, ohne auf den Server zu warten.
- [x] Zustandsfarben und -wörter (übrig / läuft / gleich vorbei / drüber) sind gesetzt, für hell und dunkel. Bei Überziehung pulsiert der Ring rot und zählt mit „+“ hoch.
- [x] Gesynct wird beim App-Start und nach jeder Start/Stopp-Aktion.
- [x] Ein erster Sync-Szenario-Test lässt die App-Outbox gegen den echten Server im Testhost laufen.
- [x] Ein ViewModel-Test (Robolectric) deckt die Zustände des Kind-Bildschirms ab.

## Notizen zur Umsetzung

**Endpunkt:** `POST /api/v1/child/sync` (nur Kindgerät, `ApiPaths.CHILD_SYNC`) ersetzt `GET /api/v1/child/snapshot`, das es nicht mehr gibt. Body `ChildSyncRequest(sessions: List<SessionUpdate>)`, Antwort der ganze `ChildSnapshot`. `SessionUpdate` = Sitzungs-UUID, `startAgeMillis`, `endAgeMillis?` (Alter bei Sendung, monotone Uhr; ein Stopp bringt seinen Start immer mit). Ein leerer Batch ist der reine Abgleich (App-Start). Fehler: `400` bei einer ID, die keine UUID ist, `403` bei einer Sitzung eines anderen Kindes (der ganze Batch wird zurückgerollt), `401`/`403` wie üblich.

**Server-Regeln (`ChildService.sync`, eine Transaktion):** Zeit = Empfangszeit − Alter, Alter < 0 zählt als 0 (nie in der Zukunft); Start einer schon bekannten Sitzung bleibt, wie er ist (wiederholte Batches ändern nichts, auch wenn das Alter inzwischen größer ist); Ende nur setzen, solange keins da ist (frühere Ende gewinnt), Grund `STOPPED`, Ende nie vor dem Start; Batch in Startreihenfolge; nie ein Start abgelehnt. Kein neues Schema: die Tabelle `sessions` aus Migration 002 reicht (`recorded_offline` bleibt für Ticket 08). Das Kappen auf „Ende der vorigen Sitzung“ und das Markieren „offline erfasst“ kommen mit Ticket 08.

**Outbox (Room, Version 2, `Migration` 1→2, `app/schemas/.../2.json` committet):** `outbox_session`, eine Zeile je Sitzung mit dem ganzen Zustand: `startMonotonicMillis`/`startWallMillis`, `endMonotonicMillis?`/`endWallMillis?`, `revision`. Die Wanduhrzeiten braucht die UI sofort (und Ticket 08 nach einem Neustart); die monotonen die Alter. Nach einem Neustart des Geräts sind die monotonen Werte ungültig, das Alter wird auf 0 geklemmt. Das behandelt Ticket 08 (Wanduhrzeit, korrigiert um den Versatz aus dem letzten Snapshot). Zugestellte Zeilen löscht der Sync im selben Room-Transaktionsblock, in dem er den Snapshot speichert, und nur bei unveränderter `revision`; ein Stopp während eines laufenden Syncs geht so nicht verloren (Test). Ein Stopp für eine Sitzung, die nur im Snapshot steht (Start schon zugestellt), legt die Zeile mit Start und Ende neu an.

**App:** `ChildRepository` hat `startSession`, `stopSession`, `sync` (ersetzt `refresh`; `ChildSyncResult` ersetzt `ChildRefreshResult`) und `local` (Snapshot plus Outbox). `toKindStand(pending)` legt die Outbox über den Snapshot; ein im Snapshot schon beendete Sitzung bleibt wie am Server (frühere Ende gewinnt). `ChildHomeViewModel.onAction()` entscheidet unter einem `Mutex` aus dem frischen lokalen Stand (Start / Stopp / nichts bei Restzeit 0, `ChildAction`) und synct danach sofort; gesynct wird außerdem beim Start des Bildschirms. Die UI hat Knopf in Zustandsfarbe („Start“, „Stopp“, deaktiviert „Keine Zeit mehr übrig“), den roten pulsierenden Ring in der Überziehung (der „+“-Zähler war schon da) und auf der Eltern-Statuskarte „Sitzung läuft seit HH:mm Uhr“ (Familien-Zeitzone, neues optionales Feld `timeZone` im Eltern-Snapshot). Zustandsfarben aus Ticket 04 unverändert übernommen.

**Test-Gerüst:** `TestServer` ist jetzt ein Test-Fixture von `server` (`java-test-fixtures`, `server/src/testFixtures`, ohne kotlin-test, damit JUnit 4 der App und JUnit 5 des Servers sich nicht beißen); die App nutzt es über `testImplementation(testFixtures(project(":server")))` (einzige Änderung an `app/build.gradle.kts`). Das Sync-Szenario-Gerüst `syncScenario` (`app/src/test/.../sync/SyncScenario.kt`) ist in `AGENTS.md` beschrieben. Es leitet die Requests der App über eine `MockEngine` an den Testhost weiter (echtes HTTP-Format, echte Routen und SQLite), nur das Netz ist simuliert. Die Migration testet `AppDatabaseMigrationTest` aus dem Schema-JSON (kein `MigrationTestHelper`, der brächte die Schemas als Assets in den APK).

**Nicht automatisiert prüfbar (am Gerät):** das Aussehen des Knopfs und des pulsierenden Rings in hell und dunkel, die Ring-Animation selbst.

**Abweichungen:** keine.
