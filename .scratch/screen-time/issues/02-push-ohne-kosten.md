# Wie erreichen Server-Ereignisse kostenlos die Android-Geräte?

Type: research
Status: resolved
Blocked by: -

## Question

Welche kostenlose Möglichkeit gibt es, Ereignisse vom Homelab-Server zeitnah an die Android-Geräte zu bringen?
(a) Benachrichtigung „Kontingent abgelaufen / Überziehung“ an die Elterngeräte
(b) „Kontingent wurde geändert, Alarm neu planen“ an das Kindgerät, auch im Standby

Kandidaten vergleichen: **Firebase Cloud Messaging** (wirklich dauerhaft kostenlos? Voraussetzungen: Google-Konto, `google-services.json`, Sideloading ok?), **ntfy** self-hosted (Docker, Android-App/Library, Akku, Zustellung im Doze), **UnifiedPush**, eigener WebSocket/SSE im Foreground Service. Kriterien: Kosten, Zuverlässigkeit im Doze, Aufwand im Homelab, Aufwand in der App, Abhängigkeit von Google.

## Answer

Findings: branch `research/push-ohne-kosten`, file `research/02-push-ohne-kosten.md` (local only; push via SSH failed).

Recommendation: **self-hosted ntfy (Docker) + UnifiedPush**, with FCM as plan B (it is free on the Spark plan, but high-priority messages without a visible notification can be downgraded and arrive late in Doze). The push is only a "please resync" signal. The device fetches its state over REST and plans the alarm locally. It also resyncs on app start/unlock. Caveat: sources were only verified through search excerpts (firewall). A real Doze test on the tablet is still needed.
