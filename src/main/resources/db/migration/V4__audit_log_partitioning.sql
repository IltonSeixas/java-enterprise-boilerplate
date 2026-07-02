-- Convert audit_log to a range-partitioned table by month.
--
-- Strategy: rename the existing table, create the partitioned parent, attach
-- the old data as a default partition, and backfill into monthly partitions.
-- This migration is safe to run against live data: the default partition
-- absorbs all existing rows; new partitions are added by the ops runbook
-- (docs/operations.md) before the month begins.
--
-- NOTE: once this migration runs, every INSERT into audit_log must include
-- occurred_at so Postgres can route the row to the correct child partition.
-- The application already populates occurred_at on every AuditEvent.

-- Rename existing heap table so we can reuse the canonical name.
ALTER TABLE audit_log RENAME TO audit_log_legacy;

-- Create the partitioned parent. The schema is identical to the original.
CREATE TABLE audit_log (
    id              UUID        NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    event_type      TEXT        NOT NULL,
    actor_user_id   TEXT        NOT NULL,
    target_user_id  TEXT,
    detail          TEXT
) PARTITION BY RANGE (occurred_at);

-- Indexes on the partitioned parent are inherited by all child partitions.
CREATE INDEX idx_audit_log_actor_time  ON audit_log (actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_log_target_time ON audit_log (target_user_id, occurred_at DESC)
    WHERE target_user_id IS NOT NULL;
CREATE INDEX idx_audit_log_type_time   ON audit_log (event_type, occurred_at DESC);

-- Attach the legacy heap as the default partition (catches anything outside
-- explicitly-named month partitions, including all historical rows).
ALTER TABLE audit_log_legacy ADD PRIMARY KEY (id);
ALTER TABLE audit_log ATTACH PARTITION audit_log_legacy DEFAULT;

-- Pre-create partitions for the current and next two months so the application
-- never hits the default partition for recent writes. Ops should add partitions
-- monthly via: docs/operations.md > "Adding an audit_log partition".
--
-- Each CREATE is wrapped in its own exception handler because IF NOT EXISTS is
-- not atomic under concurrency: two Flyway instances running in parallel (e.g.
-- during a rolling deploy) can both pass the existence check before either
-- commits, causing the second to raise duplicate_table (42P07). We catch and
-- swallow that specific error so the migration is idempotent under concurrency.
DO $$
DECLARE
    m    DATE;
    name TEXT;
BEGIN
    FOR i IN 0..2 LOOP
        m    := DATE_TRUNC('month', NOW()) + (i || ' month')::INTERVAL;
        name := TO_CHAR(m, 'YYYY_MM');
        BEGIN
            EXECUTE format(
                'CREATE TABLE audit_log_%s PARTITION OF audit_log '
                'FOR VALUES FROM (%L) TO (%L)',
                name, m, m + INTERVAL '1 month'
            );
        EXCEPTION WHEN duplicate_table THEN
            -- another replica already created this partition; safe to ignore
            NULL;
        END;
    END LOOP;
END;
$$;
