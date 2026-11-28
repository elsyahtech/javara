CREATE TABLE javara_user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES javara_users(id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    authentication_methods VARCHAR(100) NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_sessions_user_id ON javara_user_sessions (user_id);
CREATE INDEX idx_user_sessions_expires_at ON javara_user_sessions (expires_at);

CREATE TABLE javara_refresh_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES javara_user_sessions(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_refresh_tokens_expires_at ON javara_refresh_tokens (expires_at);
CREATE INDEX idx_refresh_tokens_session_id ON javara_refresh_tokens (session_id);
