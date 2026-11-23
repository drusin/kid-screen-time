# Recherche: APK-Verteilung und Updates ohne Play Store

Ticket: `.scratch/screen-time/issues/03-apk-verteilung-updates.md`
Stand: 2026-11-23

> **Hinweis zur Quellenlage:** Die Sandbox-Firewall hat direkte Abrufe von `developer.android.com`, `support.google.com`, `developers.google.com`, `source.android.com` und `samsung.com` blockiert (Freigabe ausstehend). Die Aussagen unten stammen aus Suchauszügen genau dieser offiziellen Seiten (Primärquellen, verlinkt). Wörtliche Zitate sind als solche markiert. Vor der Umsetzung sollte man die Zahlen zur Developer Verification (Gerätelimit, Termine) noch einmal direkt auf den Seiten prüfen, weil Google sie 2026 mehrfach geändert hat.

## Empfehlung (kurz)

1. **Kostenlosen „Limited distribution“-Account in der Android Developer Console anlegen**, die Package-ID registrieren und die drei Familiengeräte autorisieren. Ab 2027 setzt Android die Developer Verification weltweit für *alle* Installationsquellen durch. Damit bleibt die App ohne Umweg über ADB oder den „Advanced flow“ installierbar und aktualisierbar. Kosten: 0 €, und für 3 Geräte reicht das Limit.
2. **Verteilung: GitHub Releases + Obtainium** auf allen drei Geräten. Obtainium prüft auf neue Releases, benachrichtigt und installiert. Als Fallback geht dieselbe APK auch per Direktlink aus dem Homelab (Obtainium kann auch „Direct APK Link“/HTML-Quellen).
3. **Kein eigener In-App-Updater** für den Anfang. Das ginge (PackageInstaller + `REQUEST_INSTALL_PACKAGES`), ist aber Mehraufwand ohne echten Vorteil gegenüber Obtainium. Eine einfache „Neue Version verfügbar“-Anzeige über den Homelab-Server ist optional.
4. **Ein Release-Keystore, gut gesichert** (Passwortmanager + Offline-Backup). Mit einem verlorenen Schlüssel sind keine Updates mehr möglich, nur noch Deinstallieren und Neuinstallieren (Daten weg).
5. **Samsung Auto Blocker** muss auf dem Tablet für Installation und Updates aus. Das ist eine einmalige Einstellung, die man aber kennen muss.

## 1. Android Developer Verification (2026/2027): betrifft uns das?

**Ja, ab 2027 auch in Deutschland. Für uns ist das aber kostenlos lösbar.**

- **Zeitplan:** Seit dem 30.09.2026 wird die Verifizierung in Brasilien, Indonesien, Singapur und Thailand durchgesetzt. Ab **Januar 2027** startet der weltweite Rollout, der sich über mehrere Monate auf alle Länder und auf **alle Installationsquellen** auf zertifizierten Android-Geräten ausweitet. Laut Distributor-Guide ist der **1. März 2027** das Zieldatum. ([Expanding Android developer verification in 2027](https://developer.android.com/blog/posts/android-developer-verification-2027), [Resources for app distributors](https://developer.android.com/developer-verification/guides/distributor-resources))
- **Limited distribution account (für Hobbyisten):** Kostenlos, ohne Ausweis. Nötig sind ein Google-Konto mit 2-Schritt-Verifizierung und ein verknüpftes Google-Payments-Profil. Laut der Seite „Get started“: „Limited distribution has no registration fee, and you can distribute your apps to up to 20 devices“. Die volle Verteilung kostet einmalig 25 USD plus Ausweis. ([Get started with an Android Developer Console account](https://support.google.com/android-developer-console/answer/16604405?hl=en), [Limited distribution accounts](https://support.google.com/android-developer-console/answer/17131204?hl=en), [FAQ](https://developer.android.com/developer-verification/guides/faq))
  - Gerätelimit: Die meisten Seiten (FAQ, „Choose a distribution“) nennen 20 Geräte, einige ältere Seiten 50. Für 3 Geräte ist das egal.
  - Autorisierung pro Gerät: Am Gerät wird ein Code erzeugt (per QR-Code oder Link) und in der Console eingetragen. Laut Help Center können nicht autorisierte Geräte registrierte Apps weder installieren noch Updates dafür bekommen. ([Limited distribution accounts](https://support.google.com/android-developer-console/answer/17131204?hl=en))
  - Package-Namen: Registrieren lassen sich nur Namen, die auf Android noch nie gesehen wurden. Ein späterer Wechsel zum vollen Account ist möglich, umgekehrt nicht. ([Limited distribution accounts](https://support.google.com/android-developer-console/answer/17131204?hl=en), [Choose a distribution](https://support.google.com/android-developer-console/answer/16640817?hl=en))
  - Verfügbarkeit: Early Access ab Juni 2026, weltweit ab August 2026. ([Building a safer ecosystem together, Juni 2026](https://android-developers.googleblog.com/2026/06/android-developer-verification.html))
- **ADB ist nicht betroffen:** „There are no changes to how ADB works“. Auch die Wartezeit gilt für ADB-Installationen nicht. ([FAQ](https://developer.android.com/developer-verification/guides/faq), [Let's talk security, 2025](https://android-developers.googleblog.com/2025/09/lets-talk-security-answering-your-top.html)) Für eine Familien-App ist ADB aber unpraktisch: Jedes Update bräuchte Kabel oder Wireless Debugging am Kindertablet.
- **„Advanced flow“ für nicht verifizierte Apps:** Einmalig in den Einstellungen. Er umfasst eine **24-Stunden-Wartezeit**, danach eine Bestätigung per Biometrie oder PIN, und gilt dann wahlweise **7 Tage oder unbegrenzt**. Bei jeder Installation erscheint weiter eine Warnung mit „Install anyway“. ([Allow app installs from unverified developers](https://support.google.com/android/answer/17588095), [FAQ](https://developer.android.com/developer-verification/guides/faq)) Das geht als Notlösung, ist aber auf jedem Gerät einzeln einzurichten und bringt bei jedem Update eine Warnung. Auf dem Kindertablet wäre es zudem eine dauerhaft offene Tür für beliebige unverifizierte Apps.
- **Fazit:** Der Limited-distribution-Account ist der sauberste kostenlose Weg. Der Advanced flow oder ADB bleiben als Fallback.

## 2. Verteilungswege im Vergleich

| Weg | Installation | Updates | Aufwand | Bewertung |
|---|---|---|---|---|
| **GitHub Releases + Obtainium** | Obtainium einmal installieren, Repo-URL hinzufügen | Obtainium prüft periodisch, benachrichtigt und installiert (Bestätigung per Tap) | Gering, Release per GitHub Actions | **Empfohlen** |
| Homelab-Download (Browser) | APK-Link im Browser öffnen | Manuell: Link erneut öffnen | Minimal, aber keine Update-Benachrichtigung | Fallback |
| Homelab + Obtainium („Direct APK Link“/HTML) | wie oben | wie Obtainium | Gering | Gute Alternative bei privatem Repo |
| In-App-Update-Check | Erstinstallation trotzdem über einen anderen Weg | App lädt die APK und ruft den PackageInstaller auf | Mittel bis hoch (Download, Session, Berechtigung) | Später optional |

**Obtainium:** „Get Android app updates straight from the source“. Unterstützt werden GitHub, GitLab, Forgejo/Codeberg, F-Droid, „Direct APK Link“ und eine „HTML“-Quelle für „Any other URL that returns an HTML page with links to APK files“. Lizenz: GPL-3.0. ([Obtainium README](https://github.com/ImranR98/Obtainium)) Stille Hintergrund-Updates sind mit Shizuku möglich ([Developer Guide](https://github.com/imranr98/obtainium/blob/main/docs/DEVELOPER_GUIDE.md)). Mit dem normalen Installer klappen sie nur unter den Android-Bedingungen aus Abschnitt 3: Android 12+, Obtainium hat die App installiert, und die App hat ein aktuelles targetSdk. In der Praxis gibt es dazu Fehlerberichte ([Issue #1550](https://github.com/ImranR98/Obtainium/issues/1550)). Rechne also damit, dass ein Update einen Tap braucht.

Achtung: Ist das GitHub-Repo privat, braucht Obtainium ein Token. Ein öffentliches Repo nur für Releases oder der Homelab-Direktlink umgehen das. Der Homelab-Weg passt zur Vorgabe „keine laufenden Kosten“, GitHub Releases sind aber ebenfalls kostenlos.

## 3. In-App-Update und PackageInstaller (falls später gewünscht)

- Ab Android 8 (API 26) braucht eine App, die andere APKs installieren will (auch sich selbst), die Manifest-Berechtigung `REQUEST_INSTALL_PACKAGES`. Zusätzlich muss der Nutzer „Unbekannte Apps installieren“ **pro App** erlauben. Den Status prüft `PackageManager.canRequestPackageInstalls()`, die passende Einstellungsseite öffnet `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`. Den globalen Schalter „Unbekannte Quellen“ gibt es seit Android 8 nicht mehr. ([Android 8.0 Behavior Changes](https://developer.android.com/about/versions/oreo/android-8.0-changes), [Making it safer to get apps on Android O](https://android-developers.googleblog.com/2017/08/making-it-safer-to-get-apps-on-android-o.html), [PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager))
- Dieselbe Pro-App-Erlaubnis braucht auch **Obtainium** (bzw. der Browser beim Homelab-Download). Das ist einmalig pro Gerät.
- **Updates ohne Nachfrage** (`SessionParams.setRequireUserAction(USER_ACTION_NOT_REQUIRED)`) gehen nur, wenn alle Bedingungen erfüllt sind: Der Installer ist Update-Owner bzw. Installer of Record oder die App aktualisiert sich selbst, und die Ziel-App hat ein Mindest-targetSdk (z. B. API 33+ auf Android 15, API 34+ auf Android 16, „will advance in future Android versions“). Sonst kommt `STATUS_PENDING_USER_ACTION`, also ein Bestätigungsdialog. ([PackageInstaller.SessionParams](https://developer.android.com/reference/kotlin/android/content/pm/PackageInstaller.SessionParams), [PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller))
- Konsequenz: Mit aktuellem targetSdk könnte sich die App selbst still aktualisieren. Die Developer Verification gilt aber trotzdem für die Installation.

## 4. Signaturschlüssel

- Ein Update wird nur installiert, wenn es mit demselben Zertifikat signiert ist. Laut Google bleibt der Signaturschlüssel über die gesamte Lebensdauer einer App gleich. ([Sign your app](https://developer.android.com/studio/publish/app-signing))
- Eine Schlüsselrotation gibt es seit Android 9 (APK Signature Scheme v3, ab Android 13 v3.1). Sie **setzt aber den alten Schlüssel voraus** (`apksigner rotate --old-signer …`). ([APK signature scheme v3](https://source.android.com/docs/security/features/apksigning/v3), [v3.1](https://source.android.com/docs/security/features/apksigning/v3-1), [apksigner](https://developer.android.com/tools/apksigner))
- Ohne Play App Signing gibt es keinen Rettungsweg: Bei verlorenem Schlüssel muss man deinstallieren und neu installieren, und die lokalen Daten gehen verloren. Bei uns ist der Server die Quelle der Wahrheit, deshalb wäre nur die Kopplung neu nötig.
- Praxis: Einen Keystore anlegen, Passwörter in den Passwortmanager, ein Offline-Backup machen. Wenn CI baut, den Keystore als Base64-Secret in GitHub Actions hinterlegen. **Nicht** mit dem Debug-Key verteilen, weil der pro Rechner verschieden ist. Bei der Developer Verification wird die Package-ID mit dem Signaturschlüssel verknüpft (FAQ-Abschnitt „Package name registration and signing keys“). Deshalb muss der Schlüssel feststehen, bevor man registriert.

## 5. Google Play Protect

- Play Protect prüft bei der Installation Apps aus unbekannten Quellen. Für unbekannte Apps kann der Dialog „App scan recommended“ mit „Scan app“ / „Don't install app“ erscheinen. ([Developer Guidance for Play Protect Warnings](https://developers.google.com/android/play-protect/warning-dev-guidance), [On-device protections](https://developers.google.com/android/play-protect/client-protections))
- Apps aus „Internet-Sideloading“-Quellen, die **sensible Berechtigungen** anfordern (SMS, Notification Listener, Accessibility), werden laut Google blockiert. ([Developer Guidance](https://developers.google.com/android/play-protect/warning-dev-guidance)) Unsere App braucht diese Berechtigungen nicht und sollte sie auch nicht anfordern. Alarm, exakte Alarme und Vollbild-Intent fallen nicht darunter.
- Wenn Play Protect bei einer selbst gebauten App warnt, kann man sie scannen lassen. Danach ist sie Google bekannt.

## 6. Samsung Auto Blocker

- Ist Auto Blocker an, wird jede Installation außerhalb von Galaxy Store und Play Store gestoppt. Der Menüpunkt „Unbekannte Apps installieren“ ist dann gesperrt, und **bereits sideloadete Apps lassen sich nicht aktualisieren**. ([Samsung Support: Auto Blocker](https://www.samsung.com/us/support/answer/ANS10003636/), [Samsung UK](https://www.samsung.com/uk/support/mobile-devices/protect-your-galaxy-device-with-the-new-auto-blocker-feature/))
- Auf Geräten, die mit One UI 6.1.1 ausgeliefert wurden, ist Auto Blocker **standardmäßig an**. Bei Updates von älteren Versionen bleibt die bisherige Einstellung. Zu finden unter Einstellungen > Sicherheit und Datenschutz > Auto Blocker. ([Samsung Support](https://www.samsung.com/us/support/answer/ANS10003636/))
- Konsequenz: Auf dem Tablet muss Auto Blocker aus sein, solange Updates kommen sollen. Das schwächt auch den Schutz vor fremden APKs, also ist es eine bewusste Abwägung der Eltern.

## 7. Deinstallation durch das Kind (nur Info)

- Ohne Device Owner kann eine App ihre Deinstallation nicht verhindern. `DevicePolicyManager.setUninstallBlocked` erfordert einen Device- oder Profile-Owner, und das ist laut Map out of scope. ([DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager))
- Eine aktive **Device Admin**-App (alte API) muss erst als Administrator deaktiviert werden, bevor man sie deinstallieren kann. Das ist eine Hürde, aber kein Schutz. Die Device-Admin-API ist zudem veraltet. ([Device administration overview](https://developer.android.com/work/device-admin), [DeviceAdminReceiver](https://developer.android.com/reference/android/app/admin/DeviceAdminReceiver))
- Family Link kann Apps blockieren und Downloads genehmigen lassen. Eine dokumentierte „Deinstallation nur mit Elternfreigabe“ für sideloadete Apps habe ich nicht gefunden. ([Manage your child's Google Play apps](https://support.google.com/families/answer/7103028?hl=en))
- Pragmatisch: Der Server merkt, wenn sich das Kindgerät nicht mehr meldet (Heartbeat), und benachrichtigt die Eltern. Das ist **Erkennung statt Verhinderung** und passt zu „gesperrt wird nichts“.

## Offene Punkte

- Signaturschlüssel-Registrierung im Limited-distribution-Flow direkt auf der FAQ-Seite prüfen. Der Abschnitt war in den Suchauszügen leer.
- Gerätelimit (20 vs. 50) und die Termine für Deutschland vor der Umsetzung noch einmal direkt prüfen.
- Ob Obtainium auf dem Tablet ohne Shizuku zuverlässig im Hintergrund aktualisiert, lässt sich erst auf dem Gerät testen.
