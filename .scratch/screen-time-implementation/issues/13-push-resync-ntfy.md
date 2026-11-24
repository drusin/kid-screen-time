# 13: Push-Resync über ntfy

**What to build:** Bucht ein Elternteil Bonuszeit, erscheint sie nach wenigen Sekunden am Tablet, auch im Doze. Startet oder stoppt das Kind, aktualisiert sich das Elterngerät. Der Server stößt die Geräte über den selbst gehosteten ntfy per UnifiedPush an. Die Payload ist nach WebPush (RFC 8291) Ende-zu-Ende verschlüsselt. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Resync-Signale“ und „Push über ntfy“ sowie „Weitere Hinweise“ (zu prüfen).

**Blocked by:** 05 Sitzung starten und stoppen

**Status:** ready-for-agent

- [ ] Zuerst wird geprüft und im Ticket festgehalten, ob die aktuelle UnifiedPush-Android-Bibliothek WebPush (RFC 8291) unterstützt und ob ntfy-App und Registrierung ohne schreibende Requests nach außen auskommen.
- [ ] Die App registriert sich beim Koppeln per UnifiedPush (Endpoint, öffentlicher Schlüssel, Auth-Secret) und aktualisiert die Registrierung, wenn sie sich ändert. Die Topics sind zufällig.
- [ ] Der Server publisht intern an ntfy, nur verschlüsselt.
- [ ] Resync-Signale gehen an das Kindgerät nach jeder Eltern-Aktion zu seinem Kind (und nach einem Automatischen Stopp) und an die Elterngeräte nach Start, Stopp und Checkliste.
- [ ] Ein empfangenes Push-Signal löst am Gerät einen Sync aus (Robolectric-Test).
- [ ] Ein Server-API-Test mit Fake-HTTP-Endpunkt statt ntfy prüft, dass die richtige verschlüsselte Nachricht an das richtige Topic geht.
