# 16: Verlauf

**What to build:** Die Eltern-Startseite zeigt die letzten Tage („genutzt / Kontingent“, Überziehung markiert) mit einem Link zum ganzen Verlauf. Dort stehen pro Tag die Sitzungen (aufgeteilt an Mitternacht, mit den Markierungen „Automatisch gestoppt“ und „offline erfasst“) und die Tagesanpassungen. Siehe [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md), Abschnitte „Eltern: Verlauf“ und „Modul `domain`“ (Verlauf).

**Blocked by:** 07 Automatischer Stopp, 08 Offline-Start und -Stopp robust, 09 Tagesanpassung buchen, 11 Mitternacht und Sommerzeit

**Status:** ready-for-agent

- [ ] `domain` liefert den Verlauf als Liste von Tagen mit Verbrauch, Tageskontingent, Überziehung, aufgeteilten Sitzungen und Tagesanpassungen.
- [ ] Die letzten Tage sind Teil des Eltern-Snapshots. Der vollständige Verlauf ist eine eigene Eltern-Aktion.
- [ ] Die Markierungen „Automatisch gestoppt“ und „offline erfasst“ werden angezeigt.
- [ ] `domain`-Tests und ein Server-API-Test decken den Verlauf ab, einschließlich einer Sitzung über Mitternacht.
