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

- Test-Setup: `TestServer(dir)` (`server/src/test/.../TestServer.kt`) bringt echte SQLite-Datei im `@TempDir`, `ManualClock`, Config und `ServerServices`. Ablauf: `val server = TestServer(dir).also { it.install(this) }` in `testApplication`, `server.client(this)` liefert den JSON-Client. Public-API auf `TestServer.PUBLIC` (Port 8080), Admin auf `TestServer.ADMIN` (8081); immer volle URLs. Ein zweites `TestServer` auf demselben Ordner simuliert einen Neustart. `pairDevice(client, role)` koppelt ein Gerät und liefert das Token.
- Neuer geschützter Endpunkt: in `ApiRoutes.kt` mit `authenticatedGet/Post(path, services, DeviceRole.X) { device -> ... }` definieren. Das erzwingt `401` (kein/ungültiges/entkoppeltes Token) und `403` (falsche Rolle) einheitlich. Pfade und DTOs gehören nach `api`. Jede Rolle braucht Tests für 401 und 403.
- Datenbank: `Database.transaction { connection -> ... }` mit plain JDBC. Schemaänderung = neue Datei `server/src/main/resources/db/migration/NNN_*.sql` plus Eintrag in `Database.MIGRATIONS`, alte Dateien nie ändern.
- Fehlversuche (Code, Passwort) laufen über den einen `FailureRateLimiter`. Admin-Routen nur in `AdminRoutes.kt` (Admin-Port), API nur in `ApiRoutes.kt` (öffentlicher Port).
- Umlaute in Testnamen: Gradle braucht `LC_ALL=C.UTF-8` (in der Sandbox persistent gesetzt).

## App-Muster

- DI: `AppContainer` (manuell) hält HTTP-Client, `PairingRepository` und `ParentRepository`; `KidScreenTimeApp.container` ist die einzige Instanz. Neue Repositories dort eintragen.
- Navigation: `ui/AppRoot.kt` wählt den Bildschirm anhand von `DeviceState` (ungekoppelt, Eltern, Kind). Kein NavHost; Bildschirme innerhalb einer Rolle bekommen ihren eigenen Zustand im ViewModel.
- HTTP: `createHttpClient(engine)` (Ktor, JSON mit `ignoreUnknownKeys`, Timeouts, wirft nicht bei HTTP-Fehlern). Authentifizierte Aufrufe: `bearerAuth(pairing.token())`, URL = `device.serverUrl + ApiPaths.X`. Netzfehler und Status in ein Ergebnis-Sealed-Interface übersetzen (siehe `SnapshotResult`).
- Token: nur über `TokenStore`. Tests nutzen `FakeTokenStore`, nie den Keystore.
- ViewModel-Tests: `ui/Fixtures.kt` mit `TestContainer` (Fake-Token-Speicher, `MockEngine` über `handler`) und `StateFlow.await { }`, weil MockEngine im Hintergrund antwortet. `Dispatchers.setMain(UnconfinedTestDispatcher())` im `@Before`.

## `domain`: Bildschirmzeit-Regeln

- Einstieg ist `KindStand(...).tagesstand(jetzt)` im Paket `domain.bildschirmzeit`. Die Zeit wird als `Instant` übergeben, `domain` liest nie selbst eine Uhr. Erweiterungen (fällige Zeitpunkte, Mitternacht, Verlauf) kommen als weitere Funktionen auf `KindStand`/`Tagesstand`, nicht als zweite Rechenlogik.
- `restzeit` und `ueberziehung` sind nie negativ. Details und Entscheidungen: Ticket 02, „Notizen zur Umsetzung“.
- Tests dafür nutzen `ManualClock` und gehen nur über `tagesstand`.
