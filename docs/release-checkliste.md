# Release-Checkliste (einmalige Einrichtung und jedes Release)

Diese Schritte erledigt der Nutzer selbst. Workflow: `.github/workflows/release.yml`.

## Einmalig

### 1. Release-Keystore erzeugen und sichern

```sh
keytool -genkeypair -v -keystore release.jks -alias kid-screen-time \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Bildschirmzeit"
```

- RSA 4096, Gültigkeit 10000 Tage (rund 27 Jahre, erfüllt „25+ Jahre“).
- Keystore-Passwort und Key-Passwort wählen und in den Passwortmanager legen.
- `release.jks` in den Passwortmanager (als Anhang) **und** auf ein Offline-Medium sichern. Geht der Schlüssel verloren, lässt sich die App nicht mehr aktualisieren.
- Die Datei nie ins Repo legen (`*.jks` und `*.keystore` stehen in `.gitignore`).

### 2. Secrets im Repo anlegen

GitHub: Repo `drusin/kid-screen-time` > Settings > Secrets and variables > Actions > New repository secret.

| Secret | Inhalt |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore-Passwort |
| `RELEASE_KEY_ALIAS` | `kid-screen-time` (oder der gewählte Alias) |
| `RELEASE_KEY_PASSWORD` | Key-Passwort |

Nur `release.yml` liest diese Secrets; `ci.yml` (Push, Pull Requests) nicht.

### 3. Limited-distribution-Konto und Package-Registrierung

Android Developer Console, kostenloses Konto „limited distribution“ anlegen. Den Package-Namen `io.github.drusin.kidscreentime` mit dem Release-Schlüssel registrieren (Nachweis über die Signatur der ersten signierten APK, siehe Anweisungen der Console). Das ist nötig, bevor die App auf zertifizierten Geräten ohne Hürden installierbar ist.

### 4. Container-Paket öffentlich machen

Nach dem ersten Release: GitHub > Profil > Packages > `kid-screen-time-server` > Package settings > Change visibility > Public. Danach kann der Server ohne Anmeldung gezogen werden (`docker pull ghcr.io/drusin/kid-screen-time-server:X.Y.Z`).

### 5. Obtainium einrichten

Auf den Eltern- und Kindgeräten Obtainium installieren, „App hinzufügen“ mit der URL `https://github.com/drusin/kid-screen-time`, Quelle GitHub. Pre-Releases sind abgeschaltet (es gibt keine). Obtainium vergleicht den Tag (`vX.Y.Z`) mit der installierten `versionName` (`X.Y.Z`) und lädt die APK aus dem Release. Debug- und Release-App haben verschiedene Package-Namen (`.debug`) und stehen parallel auf dem Gerät.

### 6. Samsung Auto Blocker ausschalten

Auf Samsung-Geräten: Einstellungen > Sicherheit und Datenschutz > Auto Blocker aus. Er blockiert Installationen aus anderen Quellen als Play Store und Galaxy Store, also auch Obtainium.

## Jedes Release

1. `main` ist grün (CI).
2. Tag setzen und pushen: `git tag v1.0.0 && git push origin v1.0.0`. Format immer `vX.Y.Z` (je Teil 0 bis 99). `versionCode = X*10000 + Y*100 + Z`, also nie einen älteren Tag nachträglich höher zählen.
3. Der Workflow führt das Gate aus. Schlägt es fehl, entsteht weder Release noch Image. Nach einem Fehlschlag Tag löschen (`git push --delete origin vX.Y.Z`, lokal `git tag -d vX.Y.Z`), Fehler beheben, neu taggen.
4. Ergebnis prüfen: GitHub Release mit `kid-screen-time-vX.Y.Z.apk` und automatischen Release-Notes, Image `ghcr.io/drusin/kid-screen-time-server:X.Y.Z` (`linux/amd64`, kein `latest`).
5. Update einspielen: zuerst den Server (Image-Tag in der Compose-Datei anheben, `docker compose up -d`), dann die App über Obtainium.

## Lokal prüfen (ohne Tag, ohne Secrets)

```sh
./gradlew build                                   # versionName 0.0.0-dev, Release-APK unsigniert
RELEASE_TAG=v1.2.3 ./gradlew :app:assembleRelease # versionName 1.2.3, versionCode 10203 (oder -PreleaseTag=v1.2.3)
```

Signiert wird nur, wenn `RELEASE_KEYSTORE_FILE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` und `RELEASE_KEY_PASSWORD` gesetzt sind.
