ALTER TABLE users
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0
        CHECK (failed_login_attempts >= 0),
    ADD COLUMN login_failure_window_started_at TIMESTAMPTZ,
    ADD COLUMN failed_mfa_attempts INTEGER NOT NULL DEFAULT 0
        CHECK (failed_mfa_attempts >= 0),
    ADD COLUMN mfa_failure_window_started_at TIMESTAMPTZ,
    ADD COLUMN account_locked_until TIMESTAMPTZ,
    ADD COLUMN last_successful_source_hash VARCHAR(64);
