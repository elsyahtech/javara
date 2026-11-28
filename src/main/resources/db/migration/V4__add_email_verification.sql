CREATE TABLE javara_email_verification_tokens (
    id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES javara_users(id),
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
    ON javara_email_verification_tokens (user_id, purpose);
