CREATE TABLE IF NOT EXISTS javara_users (
    id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    pending_email VARCHAR(255),
    password_reset_required BOOLEAN NOT NULL DEFAULT FALSE,
    password_reset_support_required BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    login_failure_window_started_at TIMESTAMPTZ,
    failed_mfa_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_mfa_attempts >= 0),
    mfa_failure_window_started_at TIMESTAMPTZ,
    account_locked_until TIMESTAMPTZ,
    last_successful_source_hash VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NULL,
    deleted_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_active_unique
    ON javara_users (email)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_users_email ON javara_users (email);
CREATE INDEX IF NOT EXISTS idx_users_is_active ON javara_users (is_active);
CREATE UNIQUE INDEX idx_users_pending_email_unique
    ON javara_users (pending_email)
    WHERE pending_email IS NOT NULL AND deleted_at IS NULL;