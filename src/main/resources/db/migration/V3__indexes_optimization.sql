-- Enable the pg_trgm extension for efficient substring search on name.
-- The extension is idempotent and safe to run in CI against a fresh DB.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Trigram GIN index: supports LIKE '%term%' and ILIKE searches on users.name
-- without a full table scan. Required by ListUsersUseCase nameContains filter.
CREATE INDEX IF NOT EXISTS idx_users_name_trgm ON users USING GIN (name gin_trgm_ops);

-- Partial index covering the most common listing filters. Covers queries that
-- combine role and active filters (e.g. list all active ADMINs).
CREATE INDEX IF NOT EXISTS idx_users_role_active ON users (role, active);

-- Covering index for audit_log range + actor queries (most common access pattern
-- for dashboards: "show all events by actor X in the last 7 days").
CREATE INDEX IF NOT EXISTS idx_audit_log_actor_time ON audit_log (actor_user_id, occurred_at DESC);

-- Composite index for target + time (e.g. "what happened to user Y recently").
CREATE INDEX IF NOT EXISTS idx_audit_log_target_time ON audit_log (target_user_id, occurred_at DESC)
    WHERE target_user_id IS NOT NULL;

-- Index for event_type queries (e.g. "all failed logins in the last hour").
CREATE INDEX IF NOT EXISTS idx_audit_log_type_time ON audit_log (event_type, occurred_at DESC);
