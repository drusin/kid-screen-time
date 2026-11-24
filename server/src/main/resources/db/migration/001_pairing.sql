CREATE TABLE children (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE devices (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('PARENT', 'CHILD')),
    child_id TEXT REFERENCES children (id),
    token_hash TEXT NOT NULL UNIQUE,
    paired_at INTEGER NOT NULL,
    last_seen_at INTEGER,
    push_endpoint TEXT,
    push_public_key TEXT,
    push_auth_secret TEXT,
    unpaired_at INTEGER,
    CHECK ((role = 'CHILD') = (child_id IS NOT NULL))
);

CREATE UNIQUE INDEX devices_one_active_child_device
    ON devices (child_id) WHERE role = 'CHILD' AND unpaired_at IS NULL;

CREATE TABLE pairing_codes (
    id TEXT PRIMARY KEY,
    code_hash TEXT NOT NULL UNIQUE,
    role TEXT NOT NULL CHECK (role IN ('PARENT', 'CHILD')),
    child_id TEXT REFERENCES children (id),
    new_child_name TEXT,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    redeemed_at INTEGER
)
