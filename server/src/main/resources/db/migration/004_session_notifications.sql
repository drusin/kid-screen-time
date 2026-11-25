CREATE TABLE session_notifications (
    session_id TEXT NOT NULL REFERENCES sessions (id),
    kind TEXT NOT NULL,
    day TEXT NOT NULL,
    notified_at INTEGER NOT NULL,
    PRIMARY KEY (session_id, kind, day)
)
