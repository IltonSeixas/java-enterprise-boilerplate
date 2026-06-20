CREATE TABLE IF NOT EXISTS audit_log (
    id              UUID PRIMARY KEY,
    occurred_at     TIMESTAMPTZ NOT NULL,
    event_type      TEXT NOT NULL,
    actor_user_id   TEXT NOT NULL,
    target_user_id  TEXT NOT NULL,
    detail          TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_log_actor ON audit_log(actor_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_target ON audit_log(target_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_occurred_at ON audit_log(occurred_at);
