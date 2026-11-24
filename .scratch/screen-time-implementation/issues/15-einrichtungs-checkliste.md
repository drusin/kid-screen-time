# 15: Einrichtungs-Checkliste

**What to build:** Am Tablet gibt es eine Einrichtungs-Checkliste (Symbol in der App-Leiste). Fehlende prüfbare Punkte stehen rot da, mit direktem Link in die Systemeinstellung. Nicht prüfbare Punkte erscheinen als Hinweis zum Abhaken. Fehlt etwas, zeigt der Hauptbildschirm ein rotes Banner. Die Eltern bekommen genau eine Benachrichtigung („Mia: Alarmlautstärke ist 0“), und der Punkt steht rot in ihrem Status, bis das Tablet wieder vollständig meldet. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Einrichtungs-Checkliste“ und „Eltern-Benachrichtigungen“.

**Blocked by:** 14 Eltern-Benachrichtigungen

**Status:** ready-for-agent

- [ ] Prüfbar sind: Exakte Alarme, Full-Screen-Intent, Benachrichtigungen, Akku „Nicht eingeschränkt“, Alarmlautstärke > 0, verbundener UnifiedPush-Verteiler. Geprüft wird bei jedem App-Start und Sync.
- [ ] Hinweise zum Abhaken: „Nie schlafende Apps“, Alarme bei „Nicht stören“.
- [ ] Der Sync überträgt die Liste der fehlenden prüfbaren Punkte. Der Server speichert nur die letzte Liste pro Kindgerät.
- [ ] Pro Übergang von „vollständig“ zu „etwas fehlt“ geht genau eine Benachrichtigung an alle Elterngeräte. Die nächste ist erst nach einer vollständigen Meldung möglich.
- [ ] Der Eltern-Snapshot enthält die fehlenden Punkte, und die Statuskarte zeigt sie rot.
- [ ] Server-API-Tests prüfen die Checklisten-Übergänge mit genau einer Benachrichtigung.
