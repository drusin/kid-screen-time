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
java -jar server/build/libs/kid-screen-time-server.jar   # Server starten, GET /health (Konfiguration siehe unten)
```

## Server konfigurieren

Umgebungsvariablen. Fehlt eine Pflichtvariable, startet der Server nicht.

| Variable | Pflicht | Bedeutung |
| --- | --- | --- |
| `ADMIN_PASSWORD` | ja | Passwort des Admin-Formulars |
| `FAMILY_TIME_ZONE` | ja | Zeitzone der Familie (z. B. `Europe/Berlin`), unabhängig von der Container-Zeitzone |
| `PUBLIC_SERVER_URL` | ja | öffentliche URL des Servers (wird in der App eingegeben) |
| `NTFY_PUBLIC_URL`, `NTFY_INTERNAL_URL` | ja | ntfy von außen und aus dem Server-Netz |
| `DATABASE_PATH` | nein | SQLite-Datei, Standard `data/kid-screen-time.db` |
| `PORT` / `ADMIN_PORT` | nein | öffentlicher Port (8080) und Admin-Port (8081, nicht ins Internet publizieren) |

Erstes Elterngerät koppeln: `http://<Server>:<ADMIN_PORT>/admin` im Heimnetz öffnen, Passwort eingeben, den Code samt Server-URL in der App unter „Gerät koppeln“ eintragen. Weitere Geräte koppelt ein Elterngerät über „Gerät koppeln“ (Rolle wählen, ggf. Namen des Kindes eingeben): Es zeigt einen QR-Code mit Server-URL und den Code zum Abtippen, 10 Minuten gültig. Das neue Gerät scannt den QR-Code (Google Code Scanner, braucht Google Play-Dienste) oder tippt Server-URL und Code ein.

CI: `.github/workflows/ci.yml` läuft bei Push auf `main` und bei Pull Requests und führt `./gradlew build` aus.

## Release

Ein gepushter Tag `vX.Y.Z` startet `.github/workflows/release.yml`: zuerst dasselbe Gate wie die CI (`ci.yml` als wiederverwendbarer Workflow), danach signierte APK, GitHub Release mit APK und automatischen Notes, Server-Image `ghcr.io/drusin/kid-screen-time-server:X.Y.Z` (`Dockerfile`, `linux/amd64`, kein `latest`). `versionName` = `X.Y.Z`, `versionCode = X*10000 + Y*100 + Z`, lokal ohne Tag `0.0.0-dev`. Die Version kommt aus Env `RELEASE_TAG` oder `-PreleaseTag`; die Signatur nur aus den Env-Variablen `RELEASE_KEYSTORE_FILE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` (ohne sie bleibt die Release-APK unsigniert). Einrichtung und Ablauf für den Nutzer: [docs/release-checkliste.md](docs/release-checkliste.md).

Server-Image lokal: `./gradlew :server:shadowJar && docker build -t kid-screen-time-server .`
