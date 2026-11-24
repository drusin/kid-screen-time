# Konventionen für Agents

Fachbegriffe immer wie in `GLOSSARY.md` verwenden. Spezifikation: `.scratch/screen-time/issues/15-spezifikation-v1.md`. Tickets: `.scratch/screen-time-implementation/issues/`.

## Befehle

- Vollständige Prüfung (identisch zur CI): `./gradlew build`
- Nur Tests: `./gradlew test`, nur Lint: `./gradlew :app:lint`
- JDK 21, Android SDK mit `platforms;android-37.1`; `local.properties` (`sdk.dir`) bleibt lokal.

## Module

- `domain` und `api`: reines Kotlin, keine Android- oder Server-Abhängigkeiten. Die Zeitregeln gehören nach `domain`, damit App und Server sie teilen.
- `app`: AGP 9 mit eingebautem Kotlin (kein `kotlin-android`-Plugin). Texte nur als String-Ressourcen (Deutsch). Der Name „Bildschirmzeit Dev“ steht in `src/debug/res`.
- `server`: Ktor. `serverModule(wallClock, monotonicClock)` bekommt die Uhren injiziert.
- Abhängigkeiten und Versionen nur über `gradle/libs.versions.toml`. Repos stehen nur in `settings.gradle.kts`.

## Uhren

- Produktivcode liest die Zeit nie direkt (`System.currentTimeMillis`, `Instant.now`, `Clock.System`, `SystemClock`, `measureTime` usw. sind verboten). Stattdessen `WallClock` (Kalenderzeit, kann springen) und `MonotonicClock` (Dauern) injizieren.
- Echte Uhren: `SystemWallClock`/`SystemMonotonicClock` (`domain`, JVM), `AndroidMonotonicClock` (`app`, Elapsed Realtime). Nur diese Dateien stehen in der Allow-List von `NoDirectSystemClockTest` (`domain/src/test/.../architecture`). Neue Ausnahmen nur dort eintragen und begründen.
- Tests nutzen `ManualClock` (Test-Fixture von `domain`: `testImplementation(testFixtures(project(":domain")))`). Zeit wird vorgestellt (`advance`), nie abgewartet.

## Tests

- JUnit 5 mit `kotlin-test` in `domain`, `api`, `server`. JUnit 4 plus Robolectric in `app` (Robolectric braucht JUnit 4).
- Testnamen beschreiben die Regel in Domänensprache. Getestet wird äußeres Verhalten an der Schnittstelle, keine Mocks der Datenbank (Server: echte SQLite-Datei im Temp-Ordner).
- Robolectric lädt die android-all-Jars von `repo.maven.apache.org`, weil `repo1.maven.org` in der Sandbox gesperrt ist. Die Systemproperty `robolectric.dependency.repo.url` setzt die Root-`build.gradle.kts` für alle `Test`-Tasks zentral. In einzelnen Modulen nichts nachrüsten.
- Der Architekturtest liest die Produktivquellen aller Module. Sie sind deshalb Inputs des `domain:test`-Tasks.

## Server-Muster

- Test-Setup: `TestServer(dir)` (`server/src/testFixtures/.../TestServer.kt`, ein Test-Fixture von `server`, das auch die App-Tests nutzen) bringt echte SQLite-Datei im `@TempDir`, `ManualClock`, Config und `ServerServices`. Ablauf: `val server = TestServer(dir).also { it.install(this) }` in `testApplication`, `server.client(this)` liefert den JSON-Client. Public-API auf `TestServer.PUBLIC` (Port 8080), Admin auf `TestServer.ADMIN` (8081); immer volle URLs. Ein zweites `TestServer` auf demselben Ordner simuliert einen Neustart. `pairDevice(client, role)` koppelt ein Gerät und liefert das Token.
- Kinder entstehen nur beim Einlösen eines Kindgerät-Codes (`ChildService.create`, mit Standardvorlage). Die Werte für Snapshots rechnet immer `domain` (`ChildService` lädt nur die Eingaben). Tabellen `sessions` und `daily_adjustments` existieren (Migration 002). `sessions` schreibt nur `ChildService.sync` (Upsert, Ticket 05); `daily_adjustments` kommt mit Ticket 09.
- Sync des Kindgeräts: `POST ApiPaths.CHILD_SYNC` mit `ChildSyncRequest` (offene Sitzungen als `SessionUpdate`: UUID, Alter von Start und Ende) liefert den ganzen `ChildSnapshot`; ein `GET` auf den Snapshot gibt es nicht mehr. Der Server rechnet „Empfangszeit − Alter“ (Alter < 0 zählt als 0), behält den Start einer bekannten Sitzung, lässt das frühere Ende gewinnen, lehnt nie einen Start ab und weist Sitzungen eines anderen Kindes mit `403` ab (ganzer Batch zurückgerollt). Neue Regeln fürs Sync-Protokoll (Tickets 07, 08, 12, 13) gehören nach `ChildService.sync`.
- Neuer geschützter Endpunkt: in `ApiRoutes.kt` mit `authenticatedGet/Post(path, services, DeviceRole.X) { device -> ... }` definieren. Das erzwingt `401` (kein/ungültiges/entkoppeltes Token) und `403` (falsche Rolle) einheitlich. Pfade und DTOs gehören nach `api`. Jede Rolle braucht Tests für 401 und 403.
- Datenbank: `Database.transaction { connection -> ... }` mit plain JDBC. Schemaänderung = neue Datei `server/src/main/resources/db/migration/NNN_*.sql` plus Eintrag in `Database.MIGRATIONS`, alte Dateien nie ändern.
- Zeitgesteuerte Ereignisse (Ticket 07): `EventScheduler` (`server`) plant im Prozess, ohne Job-Queue und ohne gespeicherte Planung. Er fragt jede `TimedEventSource` (`nextDue(childId, now)`, `runDue(childId, now)`, beides aus der DB gerechnet, Regeln aus `domain`) je Kind, schläft bis zum frühesten Zeitpunkt (höchstens 1 min) und rechnet neu bei `wake()` (ruft `ChildService.onStandChanged`, heute nach jedem Sync), nach jedem Ereignis und beim Start (ein Neustart holt Verpasstes nach). Neue Ereignisse (11 Mitternacht, 14 Alarm-Benachrichtigung) = neue Quelle in der Liste in `ServerServices.create`. Tests: `TestServer` startet die Schleife nicht (`runScheduler = false`); Uhr vorstellen, dann `server.services.scheduler.runDue()`. Neue Schreibwege, die den Stand eines Kindes ändern (Tagesanpassung, Regeln), rufen danach `ChildService.notifyStandChanged()` auf, damit neu gerechnet wird.
- Automatischer Stopp: `AutomaticStopEvents` (`AutomaticStop.kt`) beendet die laufende Sitzung mit dem Zeitpunkt der Grenze (nicht „jetzt“) und dem Grund `AUTOMATIC_STOP`. Der Haken für „Sitzung wurde automatisch gestoppt“ ist `services.automaticStops.onStopped { AutomaticStop -> ... }` (läuft nach dem Commit; Ticket 13: Resync-Signal, Ticket 14: Benachrichtigung).
- Fehlversuche (Code, Passwort) laufen über den einen `FailureRateLimiter`. Admin-Routen nur in `AdminRoutes.kt` (Admin-Port), API nur in `ApiRoutes.kt` (öffentlicher Port).
- Umlaute in Testnamen: Gradle braucht `LC_ALL=C.UTF-8` (in der Sandbox persistent gesetzt).

## Push (ntfy, Ticket 13)

- Server: `PushService` (`signalChild(childId)`, `signalParents()`, optional `PushSignal(title, text)`) verschlüsselt je Gerät mit `WebPushEncryption` (RFC 8291, eigene JCA-Umsetzung, Test gegen das RFC-Beispiel) und postet an `NTFY_INTERNAL_URL` + Topic des Endpoints. Läuft über einen Hintergrund-Thread, scheitert still (Signal trägt keinen Stand), `410`/`404` löscht die Registrierung. Neue Signale nur dort aufrufen, nie aus der Transaktion heraus und nach der Antwort (siehe `ApiRoutes`, Sync). Offen für spätere Tickets: Eltern-Aktionen rufen `signalChild`, Automatischer Stopp `signalChild` + `signalParents`, Checkliste `signalParents`.
- Registrierung: `POST ApiPaths.PUSH_REGISTRATION` (beide Rollen, `authenticatedPost(path, services, setOf(...))`) oder `push` beim Koppeln. Endpoint muss `<NTFY_PUBLIC_URL>/up…` sein (SSRF-Schutz), Schlüssel werden geprüft.
- Tests: `FakeNtfy` (Test-Fixture von `server`, echter HTTP-Endpunkt) plus `TestServer(dir, ntfyInternalUrl = ntfy.url)`; `server.services.push.flush()` wartet auf den Hintergrund-Thread. `WebPushReceiver` (server-Tests) entschlüsselt wie das Gerät.
- App: `ResyncPushService` (UnifiedPush-Connector 3.x, entschlüsselt selbst; nur `message.decrypted == true` zählt) → `PushHandler` (Kind: `ChildRepository.sync()`, Eltern: `signals`-Flow, `ParentHomeViewModel` lädt neu) und `PushRepository` (Registrierung zum Server, Dedupe über `PushStorage`). `PushSetup.register(activity)` läuft in `MainActivity`, sobald gekoppelt. Robolectric-Tests: `Robolectric.setupService` auf dem Test-Thread vor `syncScenario`, Container per `KidScreenTimeApp.injectContainerForTest`.

## App-Muster

- DI: `AppContainer` (manuell) hält HTTP-Client, `PairingRepository` und `ParentRepository`; `KidScreenTimeApp.container` ist die einzige Instanz. Neue Repositories dort eintragen.
- Navigation: `ui/AppRoot.kt` wählt den Bildschirm anhand von `DeviceState` (ungekoppelt, Eltern, Kind). Kein NavHost; Bildschirme innerhalb einer Rolle bekommen ihren eigenen Zustand im ViewModel.
- HTTP: `createHttpClient(engine)` (Ktor, JSON mit `ignoreUnknownKeys`, Timeouts, wirft nicht bei HTTP-Fehlern). Authentifizierte Aufrufe: `bearerAuth(pairing.token())`, URL = `device.serverUrl + ApiPaths.X`. Netzfehler und Status in ein Ergebnis-Sealed-Interface übersetzen (siehe `SnapshotResult`).
- Room: eine Datenbank `AppDatabase` im `AppContainer`. Neue Entität = Entität eintragen, `version` erhöhen, `Migration` schreiben; das exportierte Schema liegt in `app/schemas` und wird mit committet. Tests: `TestContainer.database` (In-Memory).
- Outbox (Kindgerät): Start und Stopp schreiben zuerst in Room (`OutboxSessionEntity`, eine Zeile je Sitzung mit dem ganzen Zustand, Zeitpunkte monoton und als Wanduhr, `revision`), erst danach folgt `ChildRepository.sync()` (ein Sync gleichzeitig, `Mutex`). Der Sync schickt alle offenen Zeilen als Alter (monotone Uhr), speichert den Snapshot und löscht zugestellte Zeilen im selben Room-Transaktionsblock, aber nur bei unveränderter `revision`. Der Bildschirm rechnet aus Snapshot plus Outbox (`ChildRepository.local`, `ChildSnapshot.toKindStand(pending)`), also sofort und ohne Server. Das Startverbot (Restzeit 0) prüft nur `ChildHomeViewModel`.
- Room-Migration testen: `AppDatabaseMigrationTest` baut die alte Datenbank aus dem committeten Schema-JSON und öffnet sie mit der echten `AppDatabase`. Bei jeder neuen Version dort einen Schritt ergänzen (`MigrationTestHelper` scheidet aus, weil er die Schemas als Assets bräuchte).
- Uhr eines Bildschirms: das ViewModel bietet `tick()`, die UI ruft es per `EverySecond { }` auf. Keine endlosen `delay`-Loops im ViewModel (hängen `runTest`).
- QR: Inhalt über `PairingQr` (`api`), Anzeige `QrCode` (ZXing), Scannen nur über das `QrScanner`-Interface (Google Code Scanner, ohne Kamera-Berechtigung). Tests rufen `onQrResult` auf.
- Token: nur über `TokenStore`. Tests nutzen `FakeTokenStore`, nie den Keystore.
- ViewModel-Tests: `ui/Fixtures.kt` mit `TestContainer` (Fake-Token-Speicher, `MockEngine` über `handler`) und `StateFlow.await { }`, weil MockEngine im Hintergrund antwortet. `Dispatchers.setMain(UnconfinedTestDispatcher())` im `@Before`.

## Sync-Szenario-Tests (App-Outbox gegen echten Server)

- Gerüst: `syncScenario(dir) { ... }` in `app/src/test/.../sync/SyncScenario.kt`. Echte App-Teile (Room-Outbox, `ChildRepository`, HTTP-Client mit JSON) sprechen per echtem HTTP-Round-Trip mit dem echten Ktor-Server im Testhost (`TestServer`: echte SQLite-Datei, echte Routen). Nur das Netz ist ersetzt: `online = false` lässt jeden Request mit `IOException` scheitern.
- Eine `ManualClock` treibt Server, Wanduhr und monotone Uhr der App; `advance(...)` stellt alle zugleich vor. Nichts wird abgewartet.
- Ablauf in einem Robolectric-Test (JUnit 4, `@get:Rule TemporaryFolder`): `pairChildDevice()` koppelt ein Kindgerät an ein neues Kind, dann `start()`, `stop(id)`, `sync()` (gibt `ChildSyncResult` zurück), `child.outbox()`, `child.storedSnapshot()`, `serverSnapshot()`, `serverSession(id)`, `serverSql(...)` (für Zustände, die nur der Server herstellt, z. B. Automatischer Stopp). Weitere Geräte: `device()` (zweites/ersetzendes Kindgerät, Ticket 12). Fehlendes ins Gerüst ergänzen, kein zweites bauen.
- Vorlage: `SessionSyncScenarioTest` (Ticket 05). Server-Regeln selbst prüft `ChildSyncApiTest` (reine Server-API-Tests).

## `domain`: Bildschirmzeit-Regeln

- Einstieg ist `KindStand(...).tagesstand(jetzt)` im Paket `domain.bildschirmzeit`. Die Zeit wird als `Instant` übergeben, `domain` liest nie selbst eine Uhr. Erweiterungen (fällige Zeitpunkte, Mitternacht, Verlauf) kommen als weitere Funktionen auf `KindStand`/`Tagesstand`, nicht als zweite Rechenlogik.
- `restzeit` und `ueberziehung` sind nie negativ. Details und Entscheidungen: Ticket 02, „Notizen zur Umsetzung“.
- Tests dafür nutzen `ManualClock` und gehen nur über `tagesstand`.

## Alarmkern (Kindgerät)

- Fällige Zeitpunkte rechnet nur `domain`: `KindStand.faelligeZeitpunkte(jetzt, verlauf)` liefert `FaelligeZeitpunkte` aus `Ausloeser` (heute `VORWARNUNG`, `ALARM`, `AUTOMATISCHER_STOPP`) mit Zeitpunkt. `Alarmverlauf` bringt das mit, was `domain` nicht weiß (Vorwarnung schon gegeben, letztes Klingeln). Ein neuer Auslöser (Ticket 11 Mitternacht) = Eintrag in `Ausloeser` plus Regel dort; der Compiler zwingt zur Behandlung in `AlarmKern.trigger`.
- `AlarmKern` (`app/.../alarm`) plant immer nur den frühesten Zeitpunkt mit einem `setAlarmClock` (`AlarmScheduler` ist die einzige Stelle mit `AlarmManager`). Beim Klingeln führt `trigger()` alles Fällige aus, merkt es in `AlarmMemory` (je Sitzung, SharedPreferences) und plant neu. Effekte (Ton, Vollbild) laufen über `AlarmEffects`; Tests nehmen `RecordingAlarmEffects` (`AlarmKernTest`) oder `AlarmEffects.NONE` (Standard in `TestContainer`/`SyncScenario`).
- Der Automatische Stopp stoppt lokal über `repository.stopSession(id, at = Zeitpunkt der Grenze)` (Outbox) und synchronisiert danach; nach dem Stopp gibt es keine Wiederholung mehr. Der Hook „nach jedem Start, Stopp und Sync neu planen“ ist `AlarmKern.observe`, gestartet in `KidScreenTimeApp.onCreate`: es sammelt `ChildRepository.local` (Snapshot plus Outbox). Jeder Weg, der den lokalen Stand ändert (auch ein vom Push ausgelöster `ChildRepository.sync`), plant dadurch neu, ohne dass `sync` etwas wissen muss. Neue Wege, die den Stand ändern, müssen über Room/`local` laufen, dann gilt der Hook automatisch.
- Alarm-Tests: `ShadowAlarmManager.peekNextScheduledAlarm()` gibt den geplanten Zeitpunkt zurück. Zeit kommt aus der `ManualClock`, die geplanten Zeiten sind also exakt prüfbar.
- Debug-Build: `TestSession` (Interface in `main`) ersetzt den Stand durch eine lokale 2-min-Sitzung; `createTestSession`/`DebugTools` gibt es je einmal in `src/debug` und `src/release` (release: leer). Manuelle Prüfung: `docs/alarm-checkliste.md`.

## Release

- Version und Signatur der App stehen in `app/build.gradle.kts` (Env `RELEASE_TAG`/`-PreleaseTag`, `RELEASE_KEYSTORE_*`). Keystore nie im Repo. Ohne Env baut alles unsigniert weiter; `./gradlew build` darf davon nie abhängen.
- `release.yml` ruft `ci.yml` per `workflow_call` als Gate auf; Änderungen am Gate gehören nur in `ci.yml`. Das `Dockerfile` packt das fertige Fat-JAR (`:server:shadowJar` vorher ausführen).
