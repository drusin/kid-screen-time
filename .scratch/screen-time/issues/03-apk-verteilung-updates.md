# Wie kommen APK und Updates ohne Play Store auf die Geräte?

Type: research
Status: resolved
Blocked by: -

## Question

Wie installiert und aktualisiert man eine selbst gebaute APK kostenlos auf etwa drei Familiengeräten (Samsung-Tablet, zwei Android-Handys) ohne Play Store, möglichst bequem? Optionen: GitHub Releases + Obtainium, Download aus dem Homelab, In-App-Update-Check. Zu klären sind auch die Einschränkungen: Signaturschlüssel-Handling, „Unbekannte Quellen“, Play Protect und Samsung Auto Blocker, und ob sich das auf dem Tablet des Kindes gegen Deinstallation oder Manipulation schützen lässt (nur als Info, kein Sperren).

## Answer

Findings: branch `research/apk-verteilung`, file `research/03-apk-verteilung-updates.md` (local only; push via SSH failed).

- **Distribution:** GitHub Releases + Obtainium on all devices (the homelab works as an alternative source). Expect one tap per update. No in-app updater in v1.
- **Developer verification:** Google enforces it worldwide from 2027 (target 1 March 2027), so this app is affected. Recommendation: a free "limited distribution" account in the Android Developer Console (up to 20 devices, no fee or ID), with the package name registered using the final release key. ADB installs are not affected.
- **Signing:** one release keystore, offline backup, and a GitHub secret if CI builds it. Never use the debug key.
- **Device settings:** Samsung Auto Blocker must be off on the tablet. Play Protect: don't request SMS, accessibility or notification-listener permissions.
- **Uninstall:** can't be prevented without device-owner mode. Practical alternative: the server notices when the Kindgerät stops checking in and notifies the parents.
- Caveat: the Google and Samsung pages could only be read as search excerpts (firewall).
