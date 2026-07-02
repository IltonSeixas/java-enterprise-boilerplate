-- Convert audit_log to a range-partitioned table by month.
--
-- Strategy: rename the existing table, create the partitioned parent, attach
-- the old data as the default partition, and pre-create monthly partitions.
-- This migration is safe to run against live data: the default partition
-- absorbs all existing rows; new partitions are added by the ops runbook
-- (docs/operations.md) before the month begins.
--
-- NOTE: once this migration runs, every INSERT into audit_log must include
-- occurred_at so Postgres can route the row to the correct child partition.
-- The application already populates occurred_at on every AuditEvent.
--
-- IDEMPOTENCY: each statement is wrapped in a DO block that checks whether the
-- work has already been done before attempting it. Flyway's advisory lock
-- serialises which replica applies this version, but if the migration fails
-- partway through, Flyway rolls back and the next replica retries from scratch
-- against whatever committed state V1-V3 left behind.

-- Step 1: rename heap table only if it still exists under the original name
-- and is not yet a partitioned table.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname = 'audit_log'
          AND c.relkind = 'r'  -- ordinary heap table, not partitioned
    ) THEN
        ALTER TABLE audit_log RENAME TO audit_log_legacy;
    END IF;
END;
$$;

-- Step 2: rename the three V3 indexes so their canonical names are free for
-- the new partitioned parent (indexes travel with the renamed table).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_audit_log_actor_time') THEN
        ALTER INDEX idx_audit_log_actor_time  RENAME TO idx_audit_log_legacy_actor_time;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_audit_log_target_time') THEN
        ALTER INDEX idx_audit_log_target_time RENAME TO idx_audit_log_legacy_target_time;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_audit_log_type_time') THEN
        ALTER INDEX idx_audit_log_type_time   RENAME TO idx_audit_log_legacy_type_time;
    END IF;
END;
$$;

-- Step 3: create the partitioned parent if it does not yet exist.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname = 'audit_log' AND c.relkind = 'p'
    ) THEN
        CREATE TABLE audit_log (
            id              UUID        NOT NULL,
            occurred_at     TIMESTAMPTZ NOT NULL,
            event_type      TEXT        NOT NULL,
            actor_user_id   TEXT        NOT NULL,
            target_user_id  TEXT,
            detail          TEXT
        ) PARTITION BY RANGE (occurred_at);
    END IF;
END;
$$;

-- Step 4: create parent-level indexes (inherited by all child partitions).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_audit_log_actor_time') THEN
        CREATE INDEX idx_audit_log_actor_time ON audit_log (actor_user_id, occurred_at DESC);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_audit_log_target_time') THEN
        CREATE INDEX idx_audit_log_target_time ON audit_log (target_user_id, occurred_at DESC)
            WHERE target_user_id IS NOT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_audit_log_type_time') THEN
        CREATE INDEX idx_audit_log_type_time ON audit_log (event_type, occurred_at DESC);
    END IF;
END;
$$;

-- Step 5: attach the legacy heap as the default partition only if not already
-- attached. The legacy table already has a PRIMARY KEY from V2; no new PK needed.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        JOIN pg_class p ON p.oid = i.inhparent
        WHERE p.relname = 'audit_log' AND c.relname = 'audit_log_legacy'
    ) THEN
        ALTER TABLE audit_log ATTACH PARTITION audit_log_legacy DEFAULT;
    END IF;
END;
$$;

-- Step 6: pre-create monthly partitions for the current and next two months.
-- IF NOT EXISTS is not atomic under concurrency, so each attempt is wrapped in
-- an exception handler that silently ignores duplicate_table (42P07).
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
            NULL;
        END;
    END LOOP;
END;
$$;
