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
