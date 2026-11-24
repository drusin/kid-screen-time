# Wie werden Geräte gekoppelt und der Server abgesichert?

Type: grilling
Status: resolved
Assignee: Dawid Rusin
Blocked by: -

## Question

Der Server ist aus dem Internet erreichbar. Wie koppeln sich Kindgerät und Elterngeräte, und wie wird verhindert, dass Fremde oder das Kind Eltern-Aktionen ausführen? Vorschlag aus dem Charting: QR-Code-Kopplung aus einer Eltern-Ansicht, Geräte-Token pro Gerät, keine Benutzerkonten. Offen sind: Wie wird das **erste** Elterngerät gekoppelt (Bootstrap-Secret per Env-Variable?), wie werden Geräte entfernt bzw. widerrufen, was passiert bei Geräteverlust, und reicht das gegen Brute-Force und Fremdzugriff (Rate-Limits, Token-Länge)?

## Answer

Im Grilling mit dem Nutzer am 2026-11-24 entschieden. Die Begriffe (Kopplung, Kopplungscode, Entkoppeln) stehen in [GLOSSARY.md](../../../GLOSSARY.md).

1. **Identität:** Es gibt keine Benutzerkonten. Jedes Gerät hat ein eigenes **Geräte-Token**: zufällig, 256 Bit, als Bearer-Header. Der Server speichert nur den Hash, das Gerät legt das Token im Android Keystore ab. Es läuft nicht ab und gilt bis zum Entkoppeln. Die Rolle (Kindgerät für Kind X / Elterngerät) hängt am Token und wird bei jedem Endpunkt am Server geprüft. Ein Kindgerät darf nur Sitzungen starten und stoppen und den eigenen Stand lesen.
2. **Kopplung:** Ein Elterngerät erzeugt einen **Kopplungscode** und legt dabei die Rolle fest (bei einem Kindgerät auch das Kind). Der Code erscheint als QR-Code mit Server-URL und zusätzlich als kurzer Text zum Abtippen. Er ist einmalig und 10 Minuten gültig.
3. **Erstes Elterngerät und Recovery:** Ein **Admin-Webformular** am Server erzeugt Kopplungscodes für Elterngeräte, und nur das. Kontingente, Kindgeräte und Entkoppeln bleiben in der App. Der Reverse Proxy beschränkt das Formular auf das **Heimnetz**, zusätzlich verlangt es ein **Admin-Passwort aus einer Env-Variable**. Es funktioniert jederzeit und dient damit für Bootstrap und Recovery (alle Elterngeräte verloren). Einen CLI-Befehl gibt es nicht. Verworfen wurden ein Einrichtungscode im Container-Log und ein festes Bootstrap-Secret.
4. **Entkoppeln:** Jedes Elterngerät sieht die Geräteliste (Name, Rolle, Kopplungsdatum, zuletzt gesehen) und kann jedes Gerät entkoppeln, auch sich selbst, nur nicht das **letzte** Elterngerät. Ein entkoppeltes Gerät bekommt 401 und zeigt „Gerät wurde entkoppelt“.
5. **Kindgerät ersetzen:** Es gibt genau ein aktives Kindgerät pro Kind. Wird ein neues gekoppelt, wird das alte automatisch entkoppelt. Eine laufende Sitzung läuft am neuen Gerät weiter, weil der Stand am Server liegt.
6. **Schutz gegen Fremdzugriff:** TLS nur über den Reverse Proxy, kein HTTP. Ein **globales** Rate-Limit (nicht pro IP, z. B. 10 Fehlversuche pro Minute) gilt für den Kopplungsendpunkt und das Admin-Passwort. Alle übrigen Endpunkte verlangen ein gültiges Token. Kein VPN, kein mTLS, keine IP-Allowlist, damit die App „überall mit Internet“ funktioniert und die Push-Zustellung einfach bleibt.
7. **Push-Kanal (ntfy):** Der Push-Payload trägt den anzuzeigenden Text und den nötigen Stand, damit die Notification nicht davon abhängt, ob die App im Doze selbst Netz bekommt. Der Payload ist **Ende-zu-Ende verschlüsselt nach WebPush (RFC 8291)**: Das Gerät erzeugt beim Registrieren ein Schlüsselpaar, und ntfy sieht nur Chiffretext. Die Topics sind zufällig. Nach dem Empfang gleicht das Gerät zusätzlich per REST ab. ntfy läuft ohne Benutzerverwaltung. *Bei der Umsetzung zu prüfen:* ob die aktuelle UnifiedPush-Android-Bibliothek WebPush-Verschlüsselung unterstützt.
