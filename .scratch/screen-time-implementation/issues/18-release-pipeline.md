# 18: Release-Pipeline

**What to build:** Ein gepushter Tag `vX.Y.Z` führt das Gate aus, baut die signierte APK, erstellt ein GitHub Release mit der APK als Asset und baut und pusht `ghcr.io/drusin/kid-screen-time-server:X.Y.Z`. Schlägt das Gate fehl, entsteht kein Release. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Build, Release und Betrieb“.

**Blocked by:** 04 Kindgerät koppeln und Restzeit anzeigen

**Status:** ready-for-agent

- [ ] `versionName` ist der Tag, `versionCode = X*10000 + Y*100 + Z`. Lokal ohne Tag gilt `0.0.0-dev`.
- [ ] Der Keystore kommt aus einem CI-Secret, das nur der Tag-Workflow liest, und liegt nie im Repo.
- [ ] Das Release hat automatische Release-Notes und keinen Pre-Release-Kanal.
- [ ] Das Server-Image basiert auf `eclipse-temurin:25-jre`, ist `linux/amd64`, öffentlich und hat kein `latest`.
- [ ] Debug- und Release-App lassen sich parallel installieren.
- [ ] Eine Checkliste für den Nutzer im Repo beschreibt: Release-Keystore erzeugen (RSA 4096, 25+ Jahre) und sichern, Secret anlegen, Limited-distribution-Konto, Package-Registrierung, Obtainium einrichten, Samsung Auto Blocker aus.
