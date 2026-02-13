-- PostgreSQL schema for BLACKBOX audit logging
-- Automatically run by PostgreSQL on container initialization

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    source VARCHAR(100) NOT NULL,
    client_id VARCHAR(100),
    route_id VARCHAR(100),
    details TEXT,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for querying recent events by type (used by audit dashboard)
CREATE INDEX IF NOT EXISTS idx_audit_log_event_type_timestamp
    ON audit_log (event_type, timestamp DESC);

-- Index for querying by client (used for per-client investigation)
CREATE INDEX IF NOT EXISTS idx_audit_log_client_timestamp
    ON audit_log (client_id, timestamp DESC);
