# 01: Gerüst und CI-Gate

**What to build:** Ein lauffähiges Monorepo, auf dem alle weiteren Tickets aufbauen. Ein Gradle-Build mit den Modulen `domain`, `api`, `app` und `server`, wie in der [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md) (Abschnitt „Gesamtarchitektur“) beschrieben. Wanduhr und monotone Uhr werden überall injiziert. Ein Architekturtest verbietet direkte Systemuhr-Aufrufe im Produktivcode. GitHub Actions führt bei jedem Push auf `main` und bei jedem PR alle JVM-Tests und Android Lint aus.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `domain` und `api` sind reines Kotlin ohne Android- oder Server-Abhängigkeit, `api` nutzt kotlinx.serialization.
- [x] `app` ist eine Compose-App (Material 3, Dynamic Color, `minSdk 33`, Package `io.github.drusin.kidscreentime`, nur Deutsch, Texte als String-Ressourcen). Der Debug-Build hat das Suffix `.debug` und heißt „Bildschirmzeit Dev“.
- [x] `server` ist ein Ktor-Server, der als Fat-JAR baut.
- [x] Es gibt Abstraktionen für Wanduhr und monotone Uhr, die sich im Test steuern lassen.
- [x] Der Architekturtest (z. B. Konsist) schlägt fehl, sobald Produktivcode die Systemuhr direkt aufruft.
- [x] Der CI-Workflow läuft bei Push auf `main` und bei PRs und ist grün.

## Notizen zur Umsetzung

- **CI-Status:** Der Workflow `.github/workflows/ci.yml` führt `./gradlew build` aus, genau wie lokal. Dass „CI ist grün“ stimmt, ist erst nach dem ersten Push bestätigt. Lokal ist `./gradlew build` grün. Die Action-Versionen (`actions/checkout@v6`, `actions/setup-java@v5`, `gradle/actions/setup-gradle@v5`) konnten in der Sandbox nicht gegen GitHub geprüft werden.
- **Versionen** (Version Catalog `gradle/libs.versions.toml`): Gradle 9.9.2, Kotlin 2.4.21, AGP 9.5.2 (eingebautes Kotlin, kein `kotlin-android`-Plugin), Compose BOM 2026.11.01, Ktor 3.6.1 (CIO), kotlinx.serialization 1.13.0, Shadow 9.6.1, JUnit 6.1.3 (JVM-Module), JUnit 4.13.2 + Robolectric 4.18.1 (App), Konsist 0.18.1. JVM-Ziel 21.
- **compileSdk 37.1, targetSdk 36:** Aktuelles AndroidX (Compose 1.13, core 1.19) verlangt `compileSdk` 37.1. Dafür ist `platforms;android-37.1` nötig (lokal installiert, in der CI per `sdkmanager`).
- **Uhren:** `WallClock.now(): Instant` und `MonotonicClock.elapsed(): Duration` (`domain`, `java.time`, kein kotlinx-datetime). Echte Uhren: `SystemWallClock`, `SystemMonotonicClock` (domain) und `AndroidMonotonicClock` (app, `elapsedRealtimeNanos`, weil `System.nanoTime` im Tiefschlaf stehen bleibt). `ManualClock` ist eine Test-Fixture von `domain` (`java-test-fixtures`) für alle Module.
- **Architekturtest:** `NoDirectSystemClockTest` in `domain` (Konsist liest die Quelldateien, Regeln als Regex auf Code ohne Kommentare). Er prüft die Produktivquellen aller vier Module, mit einer Allow-List aus den zwei echten Uhr-Dateien. Die Quellen aller Module sind Inputs des `domain:test`-Tasks, sonst würde Gradle den Test nach einer Änderung in einem anderen Modul überspringen. Manuell geprüft: Ein eingefügtes `System.currentTimeMillis()` im Server lässt den Test fehlschlagen.
- **Robolectric:** `robolectric.dependency.repo.url=https://repo.maven.apache.org/maven2` ist zentral in der Root-`build.gradle.kts` für alle `Test`-Tasks gesetzt. `AppSmokeTest` beweist, dass Robolectric damit startet.
- **Server:** `serverModule(wallClock, monotonicClock)` mit `GET /health` (Platzhalter, `HealthResponse` im `api`-Modul). `build` erzeugt das Fat-JAR `server/build/libs/kid-screen-time-server.jar` (per Hand gestartet und `/health` abgefragt). Das Docker-Image kommt mit Ticket 18/19.
- **App:** Platzhalter-Bildschirm „Gerät koppeln“, Dynamic Color, nur Deutsch (`localeFilters`). Der Name „Bildschirmzeit Dev“ steht in `app/src/debug/res`. `versionName 0.0.0-dev` und `versionCode 1` sind Platzhalter, die Ableitung aus dem Tag folgt in Ticket 18.
- **Lint:** `abortOnError`, die Prüfungen `GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion` sind aus, damit neue Versionen die CI nicht brechen.
