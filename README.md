# Bildschirmzeit (kid-screen-time)

Eine Android-App plus Server, mit der Eltern die Bildschirmzeit ihres Kindes als Tageskontingent festlegen. Das Kind startet und stoppt Sitzungen am Kindgerät, Vorwarnung und Alarm kommen lokal. Begriffe stehen im [Glossar](GLOSSARY.md), die Spezifikation in `.scratch/screen-time/issues/15-spezifikation-v1.md`, die Umsetzungs-Tickets in `.scratch/screen-time-implementation/issues/`.

## Module

| Modul | Inhalt |
| --- | --- |
| `domain` | Reines Kotlin (JVM): Zeit- und Kontingentregeln, Uhren-Abstraktionen (`WallClock`, `MonotonicClock`), Test-Fixture `ManualClock`. |
| `api` | Reines Kotlin mit kotlinx.serialization: DTOs und Pfade des REST-Vertrags. |
| `app` | Android-App (Compose, Material 3, Dynamic Color, `minSdk 33`, nur Deutsch). Package `io.github.drusin.kidscreentime`, Debug-Suffix `.debug`, Name „Bildschirmzeit Dev“. |
| `server` | Ktor-Server (CIO), baut als Fat-JAR `server/build/libs/kid-screen-time-server.jar`. |

## Voraussetzungen

- JDK 21
- Android SDK mit `platforms;android-37.1` und `build-tools;36.0.0`; `local.properties` mit `sdk.dir=...` (nicht im Repo)

## Befehle

```sh
./gradlew build                  # alles, was die CI ausführt: alle Tests, Android Lint, APKs, Server-Fat-JAR
./gradlew test                   # nur Tests (JVM, inkl. Robolectric und Architekturtest)
./gradlew :app:lint              # nur Android Lint
./gradlew :server:shadowJar      # nur das Fat-JAR
java -jar server/build/libs/kid-screen-time-server.jar   # Server starten (PORT, Standard 8080), GET /health
```

CI: `.github/workflows/ci.yml` läuft bei Push auf `main` und bei Pull Requests und führt `./gradlew build` aus.
