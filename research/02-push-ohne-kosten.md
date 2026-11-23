# Recherche: Wie erreichen Server-Ereignisse kostenlos die Android-Geräte?

Ticket: `.scratch/screen-time/issues/02-push-ohne-kosten.md`
Stand: 2026-11-23

> **Hinweis zur Quellenlage:** Die Sandbox-Firewall hat direkte Seitenabrufe von firebase.google.com, developer.android.com, docs.ntfy.sh und unifiedpush.org blockiert (HTTP 403 „Approval required“). Die Aussagen unten stützen sich auf Suchergebnis-Auszüge **dieser Primärseiten** (verlinkt). Wörtliche Zitate bitte vor der Umsetzung einmal gegenprüfen. Aussagen ohne Primärbeleg sind als *unbelegt* markiert.

## Kurzfassung / Empfehlung

**Empfehlung: ntfy self-hosted im Homelab + UnifiedPush in der App**, mit der ntfy-App als Distributor auf jedem Gerät. Der Server schickt Ereignisse per einfachem HTTP-POST an ntfy. Der Alarm selbst bleibt lokal geplant, Push ist nur der Auslöser für „neu synchronisieren“. Zusätzlich synchronisiert die App beim Start/Entsperren, damit eine verpasste Nachricht nie den Alarm kaputt macht.

Begründung:
- Keine Kosten, keine Google-Konsole, kein `google-services.json`, keine Abhängigkeit von Google-Deprioritisierung.
- ntfy wirbt ausdrücklich mit Zustellung **auch im Doze** über seinen Foreground Service („Instant delivery“), bei ca. 0–1 % Akku in 17 h [ntfy FAQ][ntfy-faq], [ntfy Phone][ntfy-phone].
- Für (b) ist der Fall „Datennachricht ohne sichtbare Benachrichtigung“ genau der, den FCM bei hoher Priorität deprioritisieren kann [FCM Priority][fcm-prio]. ntfy hat dieses Problem nicht.
- Über UnifiedPush ist FCM später als Fallback nachrüstbar (Embedded FCM Distributor), ohne den Server umzubauen [UP Implementations][up-impl].

**Plan B: FCM**, falls die dauerhafte Foreground-Notification von ntfy stört oder Samsung den Dienst trotz Akku-Ausnahme beendet. FCM ist kostenlos und braucht kein Homelab-Bauteil, bringt aber Google-Abhängigkeit und die Deprioritisierungsregel mit.

**Nicht empfohlen:** eigener WebSocket/SSE im Foreground Service. Das baut ntfy nach, mit dem höchsten App-Aufwand und denselben Akku- und Doze-Fragen.

## Vergleich

| Kriterium | FCM | ntfy self-hosted (+ UnifiedPush) | Eigener WebSocket/SSE im FGS |
|---|---|---|---|
| Kosten | 0 € (No-cost-Produkt, Spark-Plan ohne Zahlungsmittel) [Pricing][fb-pricing] | 0 € (Open Source, eigener Docker-Container) | 0 € |
| Doze-Zuverlässigkeit | Gut mit hoher Priorität, **aber** Herabstufung, wenn keine sichtbare Notification folgt [fcm-prio] | Gut laut ntfy, solange Instant Delivery (FGS) aktiv ist und der Akku-Ausnahme zugestimmt wurde [ntfy-faq], [ntfy-phone] | Wie ntfy, aber selbst zu beweisen. Achtung beim FGS-Typ `dataSync` (6-h-Limit ab Android 15) [fgs-timeout] |
| Aufwand Homelab | Keiner (Service-Account-Key für HTTP v1 API) | Ein Container, Reverse-Proxy, Auth/ACL für Topics [ntfy-config] | Endpunkt im eigenen Backend |
| Aufwand App | Firebase SDK, `google-services.json`, Gradle-Plugin [fcm-start] | UnifiedPush-Connector-Bibliothek; Nutzer installiert ntfy-App und wählt sie als Distributor [up-ntfy], [up-faq] | Eigener FGS, Reconnect, Backoff, Heartbeats, Akku-Ausnahme |
| Google-Abhängigkeit | Hoch (Firebase-Projekt, Play-Dienste auf dem Gerät) [fcm-start] | Keine (für self-hosted nutzt die ntfy-App kein Firebase) [ntfy-phone] | Keine |
| Sichtbarer Dauer-Hinweis | Nein | Ja, dauerhafte FGS-Notification (ausblendbar über den Kanal) [ntfy-phone] | Ja |

## Befunde im Detail

### Firebase Cloud Messaging (FCM)

- **Kosten:** FCM steht auf der Pricing-Seite unter „No-cost products“ und ist im Spark-Plan enthalten. Spark braucht kein Zahlungsmittel und ist für Produktion zulässig, solange nur No-cost-Produkte genutzt werden [fb-pricing], [fb-plans]. Ein Preis-Versprechen „für immer“ gibt es nicht; Google kann das ändern (*unbelegt, aber allgemein*).
- **Quoten:** Standardmäßig 600 000 Nachrichten pro Minute und Projekt über die HTTP v1 API. Für eine Familie irrelevant [fcm-quota].
- **Voraussetzungen:** Android 7.0+ und ein Gerät mit Google Play Store bzw. Play-Diensten. Die App muss **nicht** über Google Play verteilt werden, Sideloading ist also ok. Nötig sind ein Firebase-Projekt (Google-Konto), die registrierte Package-ID und ein `google-services.json` im App-Modul [fcm-start], [fcm-first].
- **Doze:** Nachrichten mit hoher Priorität wecken das Gerät und geben der App kurz Netz und Wakelock [doze]. FCM kann sie aber auf normale Priorität herabstufen, wenn sie wiederholt **keine sichtbare Notification** erzeugen. Normale Priorität kann im Doze verzögert werden [fcm-prio].
  - Für (a) Eltern-Benachrichtigung unkritisch (sichtbar).
  - Für (b) „Alarm neu planen“ kritisch, außer die App zeigt jedes Mal eine sichtbare Notification („Kontingent geändert: jetzt 90 min“). Das wäre fürs Kind sogar sinnvoll.
- Die Android-Doku nennt neuere Begriffe (`RemoteNotification` für zeitkritisch/sichtbar, `BackgroundSync` für Datenabgleich) statt „high priority“ [doze]. Vor der Umsetzung die aktuelle API prüfen.

### ntfy self-hosted

- **Betrieb:** Ein Go-Binary/Docker-Container, Konfiguration per `server.yml`, Umgebungsvariablen oder Flags [ntfy-config].
- **Android-Zustellung:** Die ntfy-App nutzt Firebase **nur** für ntfy.sh und nur in der Play-Variante, **nie** für self-hosted Server und gar nicht in der F-Droid-Variante. Für self-hosted läuft „Instant delivery“: ein Foreground Service mit dauerhafter Verbindung, Zustellung „auch im Doze“ [ntfy-phone].
- **Akku:** laut FAQ ca. 0–1 % in 17 h [ntfy-faq]. Ohne Instant Delivery kann es Minuten bis Stunden dauern [ntfy-phone].
- **FCM im Server:** `firebase-key-file` ist optional und dient nur dem Akkusparen. `upstream-base-url` ist nur für iOS relevant (hier egal) [ntfy-config].
- **Akku-Optimierung:** Die UnifiedPush-Doku zu ntfy empfiehlt, die Akku-Optimierung für die ntfy-App abzuschalten [up-ntfy]. Auf Samsung ist das wegen aggressiver Hintergrund-Kills ohnehin Pflicht (*nur sekundär belegt*).
- **Einbettung:** Die ntfy-Android-App ist eine eigenständige App, keine Bibliothek. Der saubere Weg in die eigene App führt über UnifiedPush (s. u.). Alternativ kann man die ntfy-App direkt Topics abonnieren lassen. Das reicht für (a), weil die Eltern nur eine Notification brauchen. Für (b) braucht die eigene App das Signal, also UnifiedPush.

### UnifiedPush

- **Prinzip:** App-Server → Push-Server des Distributors → Distributor-App auf dem Gerät → Ziel-App. Der Distributor hält **eine** Verbindung für alle Apps. Die App registriert sich beim Distributor und bekommt eine Endpoint-URL, an die der eigene Server POSTet [up-home], [up-faq].
- **ntfy als Distributor:** Die ntfy-App (mit eigenem Server) ist ein offizieller Distributor [up-ntfy], [up-distributors].
- **App-Seite:** Die Connector-Bibliothek übernimmt die Intents (REGISTER, MESSAGE, …) laut Android-Spezifikation [up-android].
- **Fallback:** Ein `embedded-fcm-distributor` lässt sich in die App einbetten, wenn kein Distributor installiert ist. Laut UX-Leitfaden soll er nicht den Vorrang vor einem installierten Distributor haben [up-impl], [up-kdoc], [up-ux].
- **Vorteil für uns:** Der Server spricht nur „HTTP-POST an Endpoint-URL“. Ob dahinter ntfy oder FCM steckt, entscheidet das Gerät.

### Eigener WebSocket/SSE im Foreground Service

- Technisch das, was ntfy intern macht. Man muss aber Reconnect, Heartbeats und Akku-Ausnahmen selbst bauen und testen.
- Ab Android 15 (Target-SDK) haben FGS vom Typ `dataSync` ein Budget von 6 h pro 24 h, danach `onTimeout()` und kein Neustart bis zur Vordergrund-Nutzung [fgs-timeout]. Eine Dauerverbindung braucht also einen anderen Typ (z. B. `specialUse`), was Play-seitig erklärt werden müsste, beim Sideloading aber egal ist (*unbelegt für `specialUse`*).
- Die Doku zum Doze sagt: FGS nicht nur deshalb starten, damit die App nicht als idle gilt. Eine Akku-Ausnahme (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) ist nur für bestimmte Fälle gedacht, die Play-Richtlinie betrifft uns ohne Play Store aber nicht [doze].
- Ob ein FGS im Doze Netz behält, war in den abrufbaren Primärquellen nicht direkt belegt. ntfys Aussage „auch im Doze“ ist der praktische Beleg [ntfy-phone]. **Muss auf dem Samsung-Tablet getestet werden** (Teststrategie, siehe Map).

## Konsequenzen für die Architektur

1. Push ist **nur ein Hinweis**, keine Datenquelle: Nachricht = „bitte synchronisieren“. Das Kindgerät holt Kontingent und Restzeit per REST und plant den Alarm lokal neu (`setAlarmClock`/Exact Alarm, eigenes Ticket).
2. Fallback-Sync bei App-Start, Entsperren und Netzwechsel, damit eine verlorene Push-Nachricht nur zu Verzögerung führt.
3. Für (b) eine **sichtbare** Notification auf dem Kindgerät zeigen („Kontingent geändert“). Das ist bei jedem Transport robust und hält einen späteren FCM-Fallback sauber.
4. Homelab: ntfy-Container hinter dem bestehenden Reverse Proxy, Topics mit Zugriffsschutz (ACL/Token). Das Backend schickt an die UnifiedPush-Endpoints der gekoppelten Geräte.

## Offene Punkte

- Praxistest: ntfy Instant Delivery auf dem Samsung-Tablet im Deep Doze (`adb shell dumpsys deviceidle force-idle`) und nach mehreren Stunden Standby.
- Ob die dauerhafte ntfy-Notification auf dem Kindgerät akzeptabel ist (ausblendbar über den Notification-Kanal [ntfy-phone]).
- Auth für ntfy-Topics bzw. UnifiedPush-Endpoints festlegen (eigenes Ticket bei Deployment).

## Quellen

[fb-pricing]: https://firebase.google.com/pricing
[fb-plans]: https://firebase.google.com/docs/projects/billing/firebase-pricing-plans
[fcm-quota]: https://firebase.google.com/docs/cloud-messaging/throttling-and-quotas
[fcm-start]: https://firebase.google.com/docs/cloud-messaging/android/get-started
[fcm-first]: https://firebase.google.com/docs/cloud-messaging/android/first-message
[fcm-prio]: https://firebase.google.com/docs/cloud-messaging/android-message-priority
[doze]: https://developer.android.com/training/monitoring-device-state/doze-standby
[fgs-timeout]: https://developer.android.com/develop/background-work/services/fgs/timeout
[ntfy-faq]: https://docs.ntfy.sh/faq/
[ntfy-phone]: https://docs.ntfy.sh/subscribe/phone/
[ntfy-config]: https://docs.ntfy.sh/config/
[up-home]: https://unifiedpush.org/
[up-faq]: https://unifiedpush.org/users/faq/
[up-distributors]: https://unifiedpush.org/users/distributors/
[up-ntfy]: https://unifiedpush.org/users/distributors/ntfy/
[up-android]: https://unifiedpush.org/developers/spec/android/
[up-impl]: https://unifiedpush.org/developers/implementations/
[up-kdoc]: https://unifiedpush.org/kdoc/embedded_fcm_distributor/
[up-ux]: https://unifiedpush.org/developers/ux/

- Firebase Pricing: <https://firebase.google.com/pricing>
- Firebase Pricing-Pläne: <https://firebase.google.com/docs/projects/billing/firebase-pricing-plans>
- FCM Throttling & Quotas: <https://firebase.google.com/docs/cloud-messaging/throttling-and-quotas>
- FCM Android Get Started: <https://firebase.google.com/docs/cloud-messaging/android/get-started>
- FCM First Message: <https://firebase.google.com/docs/cloud-messaging/android/first-message>
- FCM Android Message Priority: <https://firebase.google.com/docs/cloud-messaging/android-message-priority>
- Android Doze & App Standby: <https://developer.android.com/training/monitoring-device-state/doze-standby>
- Android FGS Timeouts: <https://developer.android.com/develop/background-work/services/fgs/timeout>
- ntfy FAQ: <https://docs.ntfy.sh/faq/>
- ntfy Phone/Android: <https://docs.ntfy.sh/subscribe/phone/>
- ntfy Config: <https://docs.ntfy.sh/config/>
- UnifiedPush Start / FAQ / Distributors / ntfy / Android-Spec / Implementations / Embedded FCM / UX: <https://unifiedpush.org/>, <https://unifiedpush.org/users/faq/>, <https://unifiedpush.org/users/distributors/>, <https://unifiedpush.org/users/distributors/ntfy/>, <https://unifiedpush.org/developers/spec/android/>, <https://unifiedpush.org/developers/implementations/>, <https://unifiedpush.org/kdoc/embedded_fcm_distributor/>, <https://unifiedpush.org/developers/ux/>
