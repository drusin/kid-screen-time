ALTER TABLE children ADD COLUMN warning_threshold_minutes INTEGER NOT NULL DEFAULT 5 CHECK (warning_threshold_minutes >= 0);

ALTER TABLE children ADD COLUMN overrun_limit_minutes INTEGER NOT NULL DEFAULT 60 CHECK (overrun_limit_minutes >= 0);

CREATE TABLE weekly_template_versions (
    child_id TEXT NOT NULL REFERENCES children (id),
    valid_from TEXT NOT NULL,
    mon INTEGER NOT NULL CHECK (mon >= 0),
    tue INTEGER NOT NULL CHECK (tue >= 0),
    wed INTEGER NOT NULL CHECK (wed >= 0),
    thu INTEGER NOT NULL CHECK (thu >= 0),
    fri INTEGER NOT NULL CHECK (fri >= 0),
    sat INTEGER NOT NULL CHECK (sat >= 0),
    sun INTEGER NOT NULL CHECK (sun >= 0),
    PRIMARY KEY (child_id, valid_from)
);

INSERT INTO weekly_template_versions (child_id, valid_from, mon, tue, wed, thu, fri, sat, sun)
    SELECT id, '1970-01-01', 60, 60, 60, 60, 60, 120, 120 FROM children;

CREATE TABLE daily_adjustments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    child_id TEXT NOT NULL REFERENCES children (id),
    day TEXT NOT NULL,
    delta_minutes INTEGER NOT NULL,
    booked_at INTEGER NOT NULL,
    device_id TEXT REFERENCES devices (id)
);

CREATE INDEX daily_adjustments_by_child_and_day ON daily_adjustments (child_id, day);

CREATE TABLE sessions (
    id TEXT PRIMARY KEY,
    child_id TEXT NOT NULL REFERENCES children (id),
    start_at INTEGER NOT NULL,
    end_at INTEGER,
    end_reason TEXT CHECK (end_reason IN ('STOPPED', 'AUTOMATIC_STOP')),
    recorded_offline INTEGER NOT NULL DEFAULT 0,
    CHECK ((end_at IS NULL) = (end_reason IS NULL))
);

CREATE INDEX sessions_by_child_and_end ON sessions (child_id, end_at)
