# Wie wird die APK gebaut, signiert und veröffentlicht?

Type: grilling
Status: resolved
Assignee: Dawid Rusin
Blocked by: -

## Question

Der Stack steht (native Kotlin/Compose, Gradle-Module `domain` und `app`, siehe [Welche App-Architektur und welcher Stack?](06-app-architektur-stack.md)), und der Update-Weg auch (GitHub Releases + Obtainium, ein Release-Keystore, siehe [Wie kommen APK und Updates ohne Play Store auf die Geräte?](03-apk-verteilung-updates.md)). Offen ist:
- Wird lokal gebaut oder in GitHub Actions (kostenlos? öffentliches oder privates Repo)?
- Wo liegt der Keystore (GitHub Secret, Offline-Backup), und wer kann signieren?
- Was löst ein Release aus (Tag, manuell)? Wie wird versioniert (`versionCode`/`versionName`)?
- Wie wird der Package-Name bei Googles Entwicklerverifizierung („limited distribution“) registriert, und wann?
- Laufen die `domain`-Tests als Gate vor dem Release?

## Answer

Im Grilling mit dem Nutzer am 2026-11-24 entschieden.

1. **Build: GitHub Actions, öffentliches Repo.** Das kostet nichts, und Obtainium braucht auf den Geräten keinen GitHub-Token. Im Repo liegen keine Secrets, die Server-URL kommt erst beim Koppeln per QR-Code. Verworfen: ein privates Repo, weil dann auf jedem Gerät ein Token nötig wäre, auch auf dem Kindertablet. Ebenso lokale Builds, weil das Handarbeit und nicht reproduzierbar ist.
2. **Auslöser:** Ein gepushter Tag `vX.Y.Z` startet den Release-Workflow. Er führt das Gate aus, baut die signierte APK und erstellt ein GitHub Release mit der APK als Asset. Die Release-Notes erzeugt GitHub automatisch. Es gibt keinen Pre-Release-Kanal.
3. **Gate:** Alle JVM-Tests (`domain`, `api`, `server`, App-Unit-Tests) plus Android Lint. Sie laufen auch bei jedem Push auf `main` und bei jedem PR. Ohne grünen Lauf gibt es kein Release. Instrumentierte Tests gehören nicht zum Gate.
4. **Versionierung:** Der Tag ist die einzige Quelle. Gradle liest ihn als `versionName` ein, `versionCode = X*10000 + Y*100 + Z`. Lokale Builds ohne Tag bekommen `0.0.0-dev`.
5. **Keystore:** Er wird einmal lokal erzeugt (RSA 4096, Gültigkeit 25+ Jahre). Keystore-Datei und beide Passwörter liegen im Passwortmanager, dazu ein Offline-Backup. In CI liegt der Keystore als Base64-Repository-Secret, die Passwörter als eigene Secrets, und nur der Tag-Workflow liest sie. Nie im Repo, auch nicht verschlüsselt. Signieren kann nur, wer Schreibzugriff auf das Repo oder Zugang zum Passwortmanager hat.
6. **Package-Name:** `io.github.drusin.kidscreentime`. Debug-Builds bekommen `applicationIdSuffix ".debug"` und den Namen „Bildschirmzeit Dev“. So lassen sie sich parallel zur Release-App installieren. Sie werden nie verteilt.
7. **Developer-Verifizierung:** Sie wird gleich beim ersten Release erledigt, bevor die App auf ein Familiengerät kommt: Keystore erzeugen, kostenloses Limited-distribution-Konto anlegen, Package-Namen mit dem Release-Schlüssel registrieren und die drei Geräte in der Console autorisieren. Die genauen Schritte gibt es bei der Umsetzung als Checkliste für den Nutzer.
