# 04: Kindgerät koppeln und Restzeit anzeigen

**What to build:** Am Elterngerät erzeugt der Elternteil einen Kopplungscode für die Rolle Kindgerät. Gibt es noch kein Kind, gibt er zuerst den Namen ein. Der Code erscheint als QR-Code (mit Server-URL) und als Text zum Abtippen, mit Countdown. Das Tablet scannt den QR-Code oder tippt den Code ein, wird zum Kindgerät und zeigt den Ring (Variante A) mit Restzeit, Zustandswort, Tageskontingent mit Aufschlüsselung und heute genutzter Zeit. Die Eltern-Statuskarte zeigt denselben Stand. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Kopplung und Geräte“, „Kind-Snapshot“, „UI Kindgerät“ und „UI Elterngerät“.

**Blocked by:** 02 `domain`: Tageskontingent und Restzeit, 03 Erstes Elterngerät koppeln

**Status:** done

- [x] Beim Koppeln wählt man zuerst die Rolle. Bei Kindgerät wird ein neues Kind angelegt, wenn noch keins existiert. Der Ersetzen-Hinweis erscheint, wenn das Kind schon ein Kindgerät hat.
- [x] Ein neues Kind bekommt eine Wochenvorlage mit sinnvollen Standardwerten, eine Vorwarnschwelle von 5 min und eine Überziehungsgrenze von 60 min.
- [x] Der Kind-Snapshot enthält alles aus der Spezifikation, auch die gültige Wochenvorlage für heute und morgen, und ist immer vollständig.
- [x] Der Server berechnet die Werte mit `domain`.
- [x] Das Kindgerät speichert den Snapshot in Room und rechnet lokal mit `domain`.
- [x] Am Kindgerät sind keine Einstellungen sichtbar. Ein Kindgerät-Token bekommt bei Eltern-Endpunkten `403`.
- [x] Die Statuskarte am Elterngerät zeigt Restzeit, Fortschrittsbalken und Aufschlüsselung.
- [x] Ein Server-API-Test deckt Kopplung, Rollenprüfung und beide Snapshots ab.

## Notizen zur Umsetzung

**Endpunkte (neu):** `POST /api/v1/parent/pairing-codes` (nur Elterngerät; Body `CreatePairingCodeRequest(role, childId | newChildName)`; Antwort `CreatePairingCodeResponse` mit Code, Ablauf, Serverzeit, Server-URL, `replacesDeviceName` (Ersetzen-Hinweis) und dem neuen Eltern-Snapshot; `400` bei ungültigem Wunsch, auch bei neuem Kindnamen, wenn es schon ein Kind gibt) und `GET /api/v1/child/snapshot` (nur Kindgerät). Der Eltern-Snapshot trägt je Kind `status`, Vorwarnschwelle, Überziehungsgrenze und die heute gültige Wochenvorlage.

**Schema (Migration 002):** `children` bekommt `warning_threshold_minutes` (5) und `overrun_limit_minutes` (60). Neu: `weekly_template_versions` (sieben Spalten Mo–So, Schlüssel Kind + „gültig ab“), `daily_adjustments` und `sessions`. Die beiden letzten Tabellen stehen schon hier, weil der Kind-Snapshot ihre Werte an `domain` gibt. Es schreibt noch niemand hinein (Ticket 09 Tagesanpassung, Ticket 05 Sitzungs-Upsert); die Tests füllen sie per SQL. Spalten und Namen dort anpassen ist dann eine neue Migration. Bestehende Kinder bekommen in der Migration die Standardvorlage.

**`domain`:** `WochenvorlageVersion.standard(gueltigAb)` (Mo–Fr 60, Sa/So 120 min), `KindStand.STANDARD_VORWARNSCHWELLE` / `STANDARD_UEBERZIEHUNGSGRENZE` und `Wochenvorlage.version(tag)`. Ein neues Kind bekommt die Standardvorlage mit „gültig ab“ = heute, damit es sofort Zeit hat (spätere Änderungen gelten erst ab morgen).

**Kind-Snapshot:** neben dem abgeleiteten `status` stehen alle Eingaben für die lokale Rechnung drin: Vorlage-Version für heute und für morgen, die Sitzungen des heutigen Tages (Ende leer oder nach Tagesbeginn) und die heutigen Tagesanpassungen. Das Kindgerät baut daraus ein `KindStand` (`ChildSnapshot.toKindStand()`) und rechnet mit `domain` selbst, auch über Mitternacht. Ticket 05 ersetzt das `GET` durch den Sync (POST mit Outbox), der denselben Snapshot liefert.

**Ersetzen:** Das Ersetzen kommt wie vom Auftrag vorgegeben erst in Ticket 12. Der Ersetzen-Hinweis erscheint trotzdem (Rollenwahl und Code-Ansicht, aus dem Eltern-Snapshot und `replacesDeviceName`). Löst jemand einen Kindgerät-Code für ein Kind mit aktivem Kindgerät ein, antwortet der Server mit `409`, der Code bleibt unverbraucht (statt `500` am Unique-Index) und die App zeigt „Das Ersetzen ist noch nicht möglich“. Ticket 12 ersetzt diese Prüfung in `PairingService.redeem` durch das Entkoppeln des alten Geräts.

**QR-Code:** Inhalt `kidscreentime://pair?server=<URL>&code=<CODE>` (`PairingQr` in `api`, getestet). Anzeige: ZXing `core` rendert die Matrix (`QrCodeMatrix`), Compose zeichnet sie schwarz auf weiß (round-trip-Test liest sie mit dem ZXing-Reader). Scannen: Google Code Scanner (`play-services-code-scanner`), läuft als eigener Bildschirm der Play-Dienste und braucht deshalb keine Kamera-Berechtigung. Er steckt hinter `QrScanner`; die Screens bauen ihn mit ihrem Activity-Context über `AppContainer.qrScannerFactory`, Tests rufen `PairDeviceViewModel.onQrResult` direkt auf. Ein gültiger QR-Code koppelt sofort. Eintippen (Server-URL + Code) funktioniert unverändert. Das Elterngerät-Admin-Formular am Server zeigt weiter nur Text.

**Room:** `AppDatabase` (Version 1, Schema-JSON in `app/schemas`, KSP-Argument `room.schemaLocation`) mit einer Entität `child_snapshot` (eine Zeile, Snapshot als JSON, plus Empfangszeit). Ticket 05 ergänzt die Outbox als weitere Entität in derselben Datenbank (Version erhöhen, `Migration` schreiben). `AppContainer` bekommt Datenbank und Uhren injiziert; `TestContainer` nutzt eine In-Memory-Datenbank und `ManualClock`.

**App:** `ChildRepository` (holt und speichert den Snapshot, `401`/Netzfehler als Ergebnis), `ChildHomeViewModel` (liest aus Room, rechnet lokal, `tick()`), `ChildHomeScreen` (Ring, Zustandswort, Tageskontingent mit Aufschlüsselung, heute genutzt, Offline-Hinweis „gerechnet mit dem Stand von …“, keine Einstellungen). `PairNewDeviceViewModel`/`-Screen` am Elterngerät (Rolle, Kindname, Ersetzen-Hinweis, QR, Code, Countdown auf der monotonen Uhr ab der Gültigkeit vom Server). Statuskarte auf der Eltern-Startseite (Restzeit, Fortschrittsbalken, Aufschlüsselung). Die Uhr jedes Bildschirms (`EverySecond`) liegt in der UI, nicht im ViewModel, weil ein endloser `delay`-Loop im ViewModel `runTest` mit gesetztem Main-Dispatcher nie beenden lässt.

**Nicht automatisiert prüfbar (am Gerät prüfen):** der Google Code Scanner (QR scannen, Modul-Download beim ersten Mal), das Aussehen von Ring, Statuskarte und QR-Anzeige in hell und dunkel sowie auf dem Tablet.

**Abweichungen:** keine. Die Zustandsfarben stehen schon in `ui/theme/StateColors.kt`, weil der Ring sie braucht; Ticket 05 darf sie feinschleifen. Offen für spätere Tickets: Behandlung von `401` am Kindgerät (12) und das Leeren der Room-Daten beim Entkoppeln (12).
