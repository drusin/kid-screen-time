# 13: Push-Resync über ntfy

**What to build:** Bucht ein Elternteil Bonuszeit, erscheint sie nach wenigen Sekunden am Tablet, auch im Doze. Startet oder stoppt das Kind, aktualisiert sich das Elterngerät. Der Server stößt die Geräte über den selbst gehosteten ntfy per UnifiedPush an. Die Payload ist nach WebPush (RFC 8291) Ende-zu-Ende verschlüsselt. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Resync-Signale“ und „Push über ntfy“ sowie „Weitere Hinweise“ (zu prüfen).

**Blocked by:** 05 Sitzung starten und stoppen

**Status:** done

- [x] Zuerst wird geprüft und im Ticket festgehalten, ob die aktuelle UnifiedPush-Android-Bibliothek WebPush (RFC 8291) unterstützt und ob ntfy-App und Registrierung ohne schreibende Requests nach außen auskommen.
- [x] Die App registriert sich beim Koppeln per UnifiedPush (Endpoint, öffentlicher Schlüssel, Auth-Secret) und aktualisiert die Registrierung, wenn sie sich ändert. Die Topics sind zufällig.
- [x] Der Server publisht intern an ntfy, nur verschlüsselt.
- [ ] Resync-Signale gehen an das Kindgerät nach jeder Eltern-Aktion zu seinem Kind (und nach einem Automatischen Stopp) und an die Elterngeräte nach Start, Stopp und Checkliste.
- [x] Ein empfangenes Push-Signal löst am Gerät einen Sync aus (Robolectric-Test).
- [x] Ein Server-API-Test mit Fake-HTTP-Endpunkt statt ntfy prüft, dass die richtige verschlüsselte Nachricht an das richtige Topic geht.

## Notizen zur Umsetzung

### Recherche (Stand 2026-11-24, Quellen)

**Ergebnis: Die Spezifikation ist umsetzbar, kein Blocker.**

- **UnifiedPush-Connector unterstützt WebPush.** Maven-Artefakt `org.unifiedpush.android:connector` (Maven Central, aktuell 3.3.6, Release 2026-11-18; Quelle: `maven-metadata.xml` und das `-sources.jar`; das Repo ist von GitHub nach Codeberg umgezogen, `scm` im POM). Die Bibliothek erzeugt pro Instanz ein P-256-Schlüsselpaar und ein Auth-Secret (`DefaultKeyManager`, privater Schlüssel per AES-GCM mit Android-Keystore-Schlüssel in den Prefs) und liefert sie mit dem Endpoint: `PushEndpoint(url, pubKeySet: PublicKeySet(pubKey, auth), temporary)`, `pubKey` = unkomprimierter P-256-Punkt, `auth` = Secret, beide base64url ohne Padding, laut KDoc „following RFC8291“. **Sie entschlüsselt selbst**: `MessagingReceiver` ruft `keyManager.decrypt` (Tink `WebPushHybridDecrypt`, `aes128gcm`, genau ein Record, Chiffretext 86…4096 Byte, `rs` ≤ 4096) und liefert `PushMessage(content, decrypted = true)`. Gelingt das nicht, kommt der Rohinhalt mit `decrypted = false` („trying with plain text“). Daher zählt in der App nur `decrypted == true`. Registrieren ohne eigenen VAPID-Schlüssel geht (`UnifiedPush.register(context)`); Verteilerwahl über `tryUseCurrentOrDefaultDistributor(activity)`. Die App braucht eine `PushService`-Unterklasse im Manifest (`org.unifiedpush.android.connector.PUSH_EVENT`).
- **Verschlüsselung am Server:** keine etablierte, leichte JVM-Bibliothek nötig. Tink `apps-webpush` (1.15.0) bräuchte Tink plus Protobuf, `web-push`-Bibliotheken BouncyCastle, für rund 60 Zeilen HKDF/ECDH/AES-GCM. Gewählt: eigene JCA-Umsetzung (`WebPushEncryption`), die den Body des RFC-8291-Beispiels (Abschnitt 5, Werte aus dem Tink-Test, der sie wörtlich aus dem RFC kopiert) **Byte für Byte reproduziert** (fester Salt und Sender-Schlüssel) und den RFC-Body mit dem Empfängerschlüssel des RFC entschlüsselt. (Die RFC-Seite selbst war von der Sandbox aus nicht erreichbar.)
- **ntfy braucht keine schreibenden Requests nach außen von Gerät oder Verteiler.** ntfy-Android (`up/BroadcastReceiver.kt`, `up/Distributor.kt`): REGISTER legt lokal ein Abo `up` + 12 Zufallszeichen an und antwortet per Broadcast mit dem Endpoint; es gibt kein POST/PUT. Das Abo selbst ist ein lesender Stream (GET). Veröffentlichen tut nur unser Server, intern über `NTFY_INTERNAL_URL`. Die Traefik-Route nur für `GET`/`HEAD`/`OPTIONS` passt also. Aus `docs/config.md` von ntfy: UnifiedPush-Topics heißen `up*` und brauchen anonymen Schreibzugriff (ohne Benutzer Standard); Veröffentlichen ohne Abonnent ergibt `507` (Rate-Limit pro Abonnent), nach 4 Tagen ohne Abonnent `410` (Topic weg); Nachrichten > 4 KB werden nicht empfohlen. `server.go`: Konstante `encodingBase64` „mainly for binary UnifiedPush messages“, `up=1` liefert nur die Discovery-Antwort `{"unifiedpush":{"version":1}}`.
- **Nicht verifizierbar ohne Gerät/ntfy-Instanz (manuell, Checkliste unten):** dass die ntfy-App den binären `aes128gcm`-Body unverändert an den Connector weiterreicht (der Quelltext von `Distributor.sendMessage` zeigt nur die Weitergabe der Bytes; die Dekodierung von ntfys base64-Kodierung liegt in einem nicht gelesenen Teil), und dass die ntfy-App auf dem Tablet im Doze zuverlässig weckt.

### Umsetzung

**Endpunkt:** `POST /api/v1/push-registration` (`ApiPaths.PUSH_REGISTRATION`), beide Rollen, Body `PushRegistration(endpoint, publicKey, authSecret)`, Antwort `204`; `401` ohne gültiges Token, `400` bei ungültigem Endpoint oder Schlüssel. `authenticatedPost` gibt es jetzt auch mit `Set<DeviceRole>`. Die Registrierung kann auch beim Koppeln (`RedeemPairingCodeRequest.push`) mitgehen und wird dort ebenso geprüft (`400`, der Code bleibt gültig).

**Schema:** keine Migration. Die Spalten `devices.push_endpoint`, `push_public_key`, `push_auth_secret` gab es schon (Migration 001).

**Server:** `PushService` (`validate`, `register`, `signalChild(childId)`, `signalParents()`, `flush()` für Tests). Ein Signal geht an alle aktiven Geräte der Zielgruppe mit Registrierung, je Gerät eigens verschlüsselt. Der Endpoint muss `<NTFY_PUBLIC_URL>/up<Zeichen>[?up=1]` sein, sonst `400`: der Server postet dorthin, ein beliebiger Endpoint wäre ein Request-Relay (SSRF). Gepostet wird an `NTFY_INTERNAL_URL` + Rest des Endpoints, mit `Content-Encoding: aes128gcm` und `TTL: 3600`, über einen Hintergrund-Thread (kein Request wartet auf ntfy). Fehler und Netzausfall werden nur geloggt (das Signal trägt keinen Stand, der nächste Sync heilt); `404`/`410` löscht die Registrierung des Geräts. `ChildService.sync` meldet in `SyncResult.Synced.changed`, ob der Batch einen neuen Start oder Stopp brachte; nur dann gehen Signale an die Elterngeräte (wiederholte Batches und leere Syncs lösen nichts aus).

**Payload:** `PushSignal(title: String?, text: String?)` (`api`), als JSON verschlüsselt. Jedes Signal ist ein Resync; `title`/`text` sind für Ticket 14 gedacht (Anzeige ohne eigenen Netzzugriff). Unbekannte Felder werden ignoriert, Ticket 14 kann den nötigen Stand ergänzen. Ist das JSON größer als 1500 Byte, geht es ohne Text raus (ein Record fasst höchstens 3993 Byte).

**App:** `ResyncPushService` → `PushHandler` (Kind: `ChildRepository.sync()`; Eltern: `signals`-Flow, `ParentHomeViewModel` lädt den Eltern-Snapshot neu) und `PushRepository` (meldet den Endpoint dem Server, sobald gekoppelt; `PushStorage` merkt „aktuell“ und „dem Server bekannt“, also keine doppelten Aufrufe und Wiederholung beim nächsten App-Start oder Endpoint-Ereignis; WorkManager gibt es erst mit Ticket 08). `MainActivity` ruft `PushSetup.register` bei jedem Start im gekoppelten Zustand und gleich nach dem Koppeln. Die Topics sind zufällig, weil ntfy-Android sie wählt (`up` + 12 Zufallszeichen). Änderungen an Gemeinsamem: `AppContainer` (neue Felder `pushRepository`, `pushHandler`, `applicationScope`), `KidScreenTimeApp` (`injectContainerForTest`), Manifest (ein `<service>`), `ChildRepository` unverändert.

**Offen für spätere Tickets (Aufrufstellen):** Eltern-Aktionen (09, 10, 12) rufen `services.push.signalChild(childId)`; Automatischer Stopp (07) `signalChild` und `signalParents`; Checkliste (15) `signalParents`; Benachrichtigungen (14) `signalParents(PushSignal(title, text))` und im App-Handler `PushHandler.signals` für die Anzeige. Beim Entkoppeln (12) sollte die App `UnifiedPush.unregister(context)` und `PushRepository.onUnregistered()` aufrufen. Die Checkliste (15) liest „Verteiler verbunden“ aus `UnifiedPush.getAckDistributor(context) != null`.

**Tests:** `WebPushEncryptionTest` (RFC-Beispiel, Round-Trip, fremdes Gerät, Längengrenze, ungültige Schlüssel), `PushApiTest` (Fake-HTTP-Endpunkt `FakeNtfy` statt ntfy: richtiges Topic, verschlüsselter Body nur für das Zielgerät lesbar, Registrierung wechselt, fremde Endpoints abgelehnt, 401, `410`, ntfy nicht erreichbar), `PushResyncTest` (Robolectric: Push löst Sync gegen den echten Server aus und stellt die Outbox zu, nicht entschlüsselte Nachricht tut nichts, Eltern-Reload, Registrierung beim Server, Wiederholung). `./gradlew build` ist grün.

**Manuelle Checkliste (nur am Gerät/mit echtem ntfy):**
- ntfy-App auf Tablet und Handy installieren, auf `NTFY_PUBLIC_URL` einrichten, als Verteiler wählen, Kind-App koppeln: In der ntfy-App erscheint ein Abo `up…`; am Server steht der Endpoint in `devices`.
- Tagesanpassung am Elterngerät buchen (Ticket 09): am Tablet in wenigen Sekunden sichtbar, auch bei Bildschirm aus und erzwungenem Doze (`adb shell dumpsys deviceidle force-idle`).
- Kind startet/stoppt: das Elterngerät aktualisiert sich.
- Ntfy-Server hinter Traefik mit nur-lesender Route: Registrierung und Push funktionieren weiter (Veröffentlichen läuft intern).
- Nach jedem Server-Update kurz wiederholen.

**Abweichungen:** Die Signale an das Kindgerät nach Eltern-Aktionen und nach dem Automatischen Stopp sowie an die Elterngeräte nach der Checkliste sind nur als Dienst (`signalChild`/`signalParents`) vorhanden und nur nach Start/Stopp des Kindes verdrahtet, weil es die Eltern-Aktionen (09, 10, 12), den Automatischen Stopp (07) und die Checklisten-Übergabe (15) noch nicht gibt; die Aufrufstellen stehen oben. Das Kriterium „Resync-Signale gehen an …“ bleibt deshalb offen (Dienst und Verdrahtung für Start/Stopp stehen, getestet); es schließt sich mit den genannten Tickets. Status trotzdem `done`: Es fehlt nichts, was ein Mensch klären müsste.
