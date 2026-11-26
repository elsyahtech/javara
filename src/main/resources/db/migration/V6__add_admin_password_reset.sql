ALTER TABLE users
    ADD COLUMN password_reset_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE password_reset_tokens (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX idx_password_reset_tokens_user_id
    ON password_reset_tokens (user_id);

CREATE TABLE admin_password_reset_audit (
    id UUID NOT NULL PRIMARY KEY,
    actor_user_id UUID REFERENCES users(id),
    target_user_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(24) NOT NULL
        CHECK (action IN ('REQUESTED', 'FORCE_REQUIRED', 'COMPLETED')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_admin_password_reset_audit_target_created
    ON admin_password_reset_audit (target_user_id, created_at DESC);
