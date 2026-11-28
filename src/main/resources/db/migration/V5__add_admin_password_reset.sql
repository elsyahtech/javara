CREATE TABLE javara_password_reset_tokens (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES javara_users(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX idx_password_reset_tokens_user_id
    ON javara_password_reset_tokens (user_id);

CREATE TABLE javara_admin_password_reset_audit (
    id UUID NOT NULL PRIMARY KEY,
    actor_user_id UUID REFERENCES javara_users(id),
    target_user_id UUID NOT NULL REFERENCES javara_users(id),
    action VARCHAR(24) NOT NULL
        CONSTRAINT chk_admin_password_reset_audit_action
        CHECK (action IN (
            'REQUESTED',
            'FORCE_REQUIRED',
            'COMPLETED',
            'EMERGENCY_RANDOMIZED',
            'SUPPORT_AUTHORIZED',
            'RECOVERY_COMPLETED'
        )),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_admin_password_reset_audit_target_created
    ON javara_admin_password_reset_audit (target_user_id, created_at DESC);
