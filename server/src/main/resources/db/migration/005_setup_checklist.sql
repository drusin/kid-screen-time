-- Einrichtungs-Checkliste (ticket 15): the last list of missing prüfbare Punkte per Kindgerät (comma separated
-- SetupItem names, empty = vollständig) and the Übergangsvermerk: 1 while the Eltern were notified about the current
-- "etwas fehlt" phase. It is reset by the next complete report, so the next Übergang notifies again.
ALTER TABLE devices ADD COLUMN setup_missing TEXT NOT NULL DEFAULT '';

ALTER TABLE devices ADD COLUMN setup_notified INTEGER NOT NULL DEFAULT 0;
