# 18: Release-Pipeline

**What to build:** Ein gepushter Tag `vX.Y.Z` führt das Gate aus, baut die signierte APK, erstellt ein GitHub Release mit der APK als Asset und baut und pusht `ghcr.io/drusin/kid-screen-time-server:X.Y.Z`. Schlägt das Gate fehl, entsteht kein Release. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Build, Release und Betrieb“.

**Blocked by:** 04 Kindgerät koppeln und Restzeit anzeigen

**Status:** done

- [x] `versionName` ist der Tag, `versionCode = X*10000 + Y*100 + Z`. Lokal ohne Tag gilt `0.0.0-dev`.
- [x] Der Keystore kommt aus einem CI-Secret, das nur der Tag-Workflow liest, und liegt nie im Repo.
- [x] Das Release hat automatische Release-Notes und keinen Pre-Release-Kanal.
- [x] Das Server-Image basiert auf `eclipse-temurin:25-jre`, ist `linux/amd64`, öffentlich und hat kein `latest`.
- [x] Debug- und Release-App lassen sich parallel installieren.
- [x] Eine Checkliste für den Nutzer im Repo beschreibt: Release-Keystore erzeugen (RSA 4096, 25+ Jahre) und sichern, Secret anlegen, Limited-distribution-Konto, Package-Registrierung, Obtainium einrichten, Samsung Auto Blocker aus.

## Notizen zur Umsetzung

- Version: `app/build.gradle.kts` liest Env `RELEASE_TAG` oder `-PreleaseTag` (Form `vX.Y.Z`, je Teil 0-99, sonst Abbruch). `versionName` ist der Tag ohne `v` (`1.2.3`), `versionCode` 10203. Ohne Tag `0.0.0-dev` / Code 1. Lokal geprüft mit `-PreleaseTag=v1.2.3` (Code 10203) und einem ungültigen Tag (Abbruch).
- Signatur nur zur Laufzeit aus `RELEASE_KEYSTORE_FILE/_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Ohne sie bleibt die Release-APK unsigniert, `./gradlew build` bleibt grün. Mit einem Test-Keystore lokal signiert und mit `apksigner verify` geprüft.
- `release.yml` (Trigger Tag `v[0-9]+.[0-9]+.[0-9]+`): Job `gate` ruft `ci.yml` per `workflow_call` auf; `apk` und `image` brauchen `gate`; `release` braucht `apk` und `image`, erstellt das Release mit `gh release create --generate-notes --verify-tag` (kein Pre-Release). Keystore-Secrets (Base64) werden nur in `release.yml` gelesen. Image nur mit Tag `X.Y.Z`, `linux/amd64`, kein `latest`; Sichtbarkeit "public" stellt der Nutzer einmalig im GHCR ein (Checkliste).
- `Dockerfile` (Basis `eclipse-temurin:25-jre`) packt das vorab gebaute Fat-JAR (`.dockerignore` lässt nur das JAR zu). Lokal gebaut und gestartet, `GET /health` lieferte `200 {"status":"ok",...}`.
- Debug und Release parallel: Debug hat `applicationIdSuffix = ".debug"` (schon aus Ticket 01), der Manifest hat keine festen Authorities.
- Nicht prüfbar ohne GitHub: tatsächlicher Lauf der Workflows. Geprüft: YAML-Syntax und `actionlint` ohne Befund. Die Action-Versionen (`docker/*@v3/v6`, `download-artifact@v5`, `upload-artifact@v4`) sind nicht gegen GitHub verifiziert.
- Checkliste für den Nutzer: `docs/release-checkliste.md`.
