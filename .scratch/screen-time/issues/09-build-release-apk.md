# Wie wird die APK gebaut, signiert und veröffentlicht?

Type: grilling
Status: open
Blocked by: -

## Question

Der Stack steht (native Kotlin/Compose, Gradle-Module `domain` und `app`, siehe [Welche App-Architektur und welcher Stack?](06-app-architektur-stack.md)), und der Update-Weg auch (GitHub Releases + Obtainium, ein Release-Keystore, siehe [Wie kommen APK und Updates ohne Play Store auf die Geräte?](03-apk-verteilung-updates.md)). Offen ist:
- Wird lokal gebaut oder in GitHub Actions (kostenlos? öffentliches oder privates Repo)?
- Wo liegt der Keystore (GitHub Secret, Offline-Backup), und wer kann signieren?
- Was löst ein Release aus (Tag, manuell)? Wie wird versioniert (`versionCode`/`versionName`)?
- Wie wird der Package-Name bei Googles Entwicklerverifizierung („limited distribution“) registriert, und wann?
- Laufen die `domain`-Tests als Gate vor dem Release?
