ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN pending_email VARCHAR(255);

CREATE UNIQUE INDEX idx_users_pending_email_unique
    ON users (pending_email)
    WHERE pending_email IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE email_verification_tokens (
    id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(24) NOT NULL
        CHECK (purpose IN ('REGISTRATION', 'EMAIL_CHANGE')),
    target_email VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    PRIMARY KEY (id),
    CHECK (
        (purpose = 'REGISTRATION' AND target_email IS NULL)
        OR (purpose = 'EMAIL_CHANGE' AND target_email IS NOT NULL)
    )
);

CREATE INDEX idx_email_verification_tokens_user_purpose
    ON email_verification_tokens (user_id, purpose);
