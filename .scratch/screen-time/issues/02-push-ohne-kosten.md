# Wie erreichen Server-Ereignisse kostenlos die Android-Geräte?

Type: research
Status: open
Blocked by: -

## Question

Welche kostenlose Möglichkeit gibt es, Ereignisse vom Homelab-Server zeitnah an die Android-Geräte zu bringen?
(a) Benachrichtigung „Kontingent abgelaufen / Überziehung“ an die Elterngeräte
(b) „Kontingent wurde geändert, Alarm neu planen“ an das Kindgerät, auch im Standby

Kandidaten vergleichen: **Firebase Cloud Messaging** (wirklich dauerhaft kostenlos? Voraussetzungen: Google-Konto, `google-services.json`, Sideloading ok?), **ntfy** self-hosted (Docker, Android-App/Library, Akku, Zustellung im Doze), **UnifiedPush**, eigener WebSocket/SSE im Foreground Service. Kriterien: Kosten, Zuverlässigkeit im Doze, Aufwand im Homelab, Aufwand in der App, Abhängigkeit von Google.
