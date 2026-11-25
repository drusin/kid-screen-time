# 14: Eltern-Benachrichtigungen

**What to build:** Alle Elterngeräte bekommen eine Heads-up-Benachrichtigung, wenn die Restzeit einer laufenden Sitzung 0 erreicht („Mia: Kontingent abgelaufen (seit 14:32)“), auch wenn das Tablet offline ist. Beim Automatischen Stopp kommt „Mia: Automatischer Stopp nach 60 min Überziehung“. Jede Benachrichtigung kommt einmal, nie doppelt und nie nachträglich. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Eltern-Benachrichtigungen“.

**Blocked by:** 07 Automatischer Stopp, 13 Push-Resync über ntfy

**Status:** done

- [x] Der Server plant „Restzeit 0“ pro Kind im Prozess und benachrichtigt alle Elterngeräte, mit dem Kindnamen vorne.
- [x] Die Payload enthält den anzuzeigenden Text, sodass die Benachrichtigung ohne eigenen Netzzugriff der App erscheint.
- [x] Erfährt der Server eine laufende Sitzung erst beim Sync und ist sie schon in der Überziehung, kommt die Alarm-Benachrichtigung jetzt, genau einmal.
- [x] Für Sitzungen, die offline schon beendet wurden, kommen keine Benachrichtigungen.
- [x] Nach einem Neustart des Servers kommt keine Benachrichtigung doppelt. Der Mechanismus (abgeleitet oder „gemeldet“-Vermerk pro Sitzung) ist im Ticket festgehalten.
- [x] Server-API-Tests stellen die Uhr vor und prüfen jeweils genau eine Benachrichtigung bei Alarm, Automatischem Stopp, nachträglich bekannter Sitzung und nach einem Neustart.


## Notizen zur Umsetzung

- **„Genau einmal, auch nach Neustart“: „gemeldet“-Vermerk, nicht abgeleitet.** Ohne Vermerk wüsste ein neu gestarteter Server nicht, ob er den Alarm einer laufenden Sitzung in der Überziehung schon gemeldet hat. Migration `004_session_notifications.sql`: Tabelle `session_notifications (session_id, kind, day, notified_at)`, Primärschlüssel `(session_id, kind, day)`. `ChildService.reportRestzeitNull` setzt den Vermerk (`INSERT OR IGNORE`) in derselben Transaktion, die entscheidet; erst danach läuft der Push. Alles andere ist abgeleitet: „fällig“ ist der Alarm-Zeitpunkt aus `domain` (`KindStand.faelligeZeitpunkte`) einer laufenden Sitzung ohne Vermerk. Der Vermerk wird vor dem Push gesetzt: ein Absturz dazwischen verliert die Benachrichtigung, statt sie zu wiederholen („nie doppelt“ geht vor).
- **Automatischer Stopp braucht keinen Vermerk:** das Ende wird atomar gesetzt (`end_at IS NULL`), also läuft der Listener genau einmal. `AutomaticStop` trägt dafür jetzt `childName` und `overrunLimit` („Mia: Automatischer Stopp nach 60 min Überziehung“). Der Haken `automaticStops.onStopped` in `ServerServices.create` schickt `signalChild` und `signalParents(PushSignal(title, text))` statt des bloßen `signalParents()` (ein Push mit Text löst am Elterngerät auch den Abgleich aus, nichts kommt doppelt).
- **Neue Quelle `RestzeitNullEvents`** (`TimedEventSource`), Reihenfolge in `ServerServices.create`: Automatischer Stopp, Restzeit 0, Mitternacht. Die Reihenfolge ist Absicht: beendet der Stopp eine Sitzung zuerst (Server war lange aus), ist sie nicht mehr laufend, und es kommt nur die Benachrichtigung zum Stopp, kein nachgeholter Alarm.
- **Sonderfälle:**
  - Sitzung erst beim Sync (oder Neustart) bekannt und schon in der Überziehung: sofort fällig, genau eine Benachrichtigung, Zeit „seit …“ = Beginn der Überziehung (nicht früher als der Sitzungsstart).
  - Offline schon beendete Sitzung (Start und Ende im selben Sync): nie laufend, keine Benachrichtigung.
  - Tagesanpassung hebt die Restzeit nach dem Alarm über 0, sie fällt erneut auf 0: **keine zweite Benachrichtigung** (einmal je Sitzung und Tag; die Eltern wissen es schon, sie haben ja gerade angepasst).
  - Sitzung über Mitternacht: der Vermerk gilt je Tag, erreicht die Restzeit des neuen Tages 0, kommt eine neue Benachrichtigung (und der Stopp danach).
  - Überziehungsgrenze 0: `domain` kennt dann keinen Alarm vor dem Stopp, es kommt nur die Stopp-Benachrichtigung.
- **Texte** in `ParentNotifications` (Titel „Bildschirmzeit“, Text mit Kindname vorne, Uhrzeit in der Familien-Zeitzone). Ticket 15 nutzt dieselbe Mechanik: `signalParents(PushSignal(...))`; eine Übergangs-Benachrichtigung braucht ihren eigenen Vermerk (eigene Tabelle oder `kind`).
- **App:** `ParentNotifier` (`AndroidParentNotifier`, Kanal `parent-notifications` mit `IMPORTANCE_HIGH`, je Benachrichtigung eine neue ID, Tippen öffnet die App). `PushHandler` zeigt bei einem Elterngerät den Text eines Signals und löst weiter den Abgleich über `signals` aus; Kindgerät, ungekoppelt und nicht entschlüsselte Nachrichten zeigen nichts. `MainActivity` fragt `POST_NOTIFICATIONS` (API 33+) am gekoppelten Elterngerät an. Lint verlangte dafür `androidx.fragment` >= 1.3.0 (Version 1.0.0 kam transitiv): jetzt explizit im Versionskatalog. `TestContainer.shownNotifications` zeichnet die gezeigten Benachrichtigungen auf.
- **Bestehende Server-Tests angepasst:** `scheduler.nextDue()` liefert jetzt zusätzlich den Zeitpunkt „Restzeit 0“. Tests, die den Stopp-Zeitpunkt prüfen, fragen `automaticStops.nextDue(childId, now)`, der Mitternachts-Test `children.nextMidnight(...)`.
- **Manuell am Gerät zu prüfen:** Heads-up-Banner bei gesperrtem und entsperrtem Bildschirm, Erscheinen im Doze, Berechtigungsdialog beim ersten Start des Elterngeräts (Android 13+).
