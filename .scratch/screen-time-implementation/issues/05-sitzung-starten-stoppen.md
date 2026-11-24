# 05: Sitzung starten und stoppen

**What to build:** Das Kind drückt den großen Knopf, die Sitzung startet, der Ring zählt `mm:ss` herunter, und der Knopf wird zu „Stopp“. Nach dem Stopp bleibt die Restzeit für später. Bei Restzeit 0 ist der Knopf deaktiviert und zeigt „Keine Zeit mehr übrig“. Die Eltern sehen „Sitzung läuft seit …“. Jede Änderung geht zuerst in die Room-Outbox, danach folgt sofort ein Sync. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Sync-Protokoll“.

**Blocked by:** 04 Kindgerät koppeln und Restzeit anzeigen

**Status:** ready-for-agent

- [ ] Beim Start erzeugt das Gerät eine Sitzungs-UUID. Jeder Sync überträgt den ganzen Zustand der Sitzung (Start, Ende oder leer), und der Server legt an oder aktualisiert (Upsert).
- [ ] Zeitpunkte werden als Alter übertragen, gemessen mit der monotonen Uhr. Der Server rechnet „Serverzeit beim Empfang − Alter“.
- [ ] Der Server lehnt keinen Start ab. Das Startverbot prüft nur das Kindgerät.
- [ ] Ein Stopp für eine schon beendete Sitzung ändert nichts, denn das frühere Ende gewinnt.
- [ ] Die UI zeigt Änderungen sofort aus dem lokalen Stand, ohne auf den Server zu warten.
- [ ] Zustandsfarben und -wörter (übrig / läuft / gleich vorbei / drüber) sind gesetzt, für hell und dunkel. Bei Überziehung pulsiert der Ring rot und zählt mit „+“ hoch.
- [ ] Gesynct wird beim App-Start und nach jeder Start/Stopp-Aktion.
- [ ] Ein erster Sync-Szenario-Test lässt die App-Outbox gegen den echten Server im Testhost laufen.
- [ ] Ein ViewModel-Test (Robolectric) deckt die Zustände des Kind-Bildschirms ab.
