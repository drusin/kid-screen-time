# 17: Elterngerät offline

**What to build:** Ohne Netz zeigt das Elterngerät den letzten bekannten Stand mit der Leiste „Offline · nur Ansicht“. Alle Aktionen sind deaktiviert. Die Statuskarte zeigt, seit wann der Stand des Kindgeräts bekannt ist. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitt „Eltern: offline“.

**Blocked by:** 09 Tagesanpassung buchen

**Status:** ready-for-agent

- [ ] Der Eltern-Snapshot wird lokal gecacht und offline gelesen.
- [ ] Offline sind Buchen, Zurücknehmen, Wochenvorlage, Regeln, Koppeln und Entkoppeln deaktiviert.
- [ ] Die Statuskarte zeigt „Stand bekannt seit …“ aus dem Snapshot.
- [ ] Das Elterngerät gleicht beim App-Start und nach jeder eigenen Aktion ab.
- [ ] Ein ViewModel-Test (Robolectric) prüft, dass die Aktionen offline deaktiviert sind.
