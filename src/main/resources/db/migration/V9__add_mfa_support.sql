CREATE TABLE user_mfa_methods (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    method VARCHAR(24) NOT NULL
        CHECK (method IN ('EMAIL_OTP', 'SMS_OTP', 'WHATSAPP_OTP', 'WEBAUTHN')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    preferred BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, method)
);

CREATE UNIQUE INDEX idx_user_mfa_methods_preferred
    ON user_mfa_methods (user_id)
    WHERE enabled = TRUE AND preferred = TRUE;

CREATE TABLE mfa_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    purpose VARCHAR(32) NOT NULL
        CHECK (purpose IN ('LOGIN', 'EMAIL_ENROLLMENT', 'PASSKEY_ENROLLMENT', 'REQUIRED_ENROLLMENT')),
    method VARCHAR(24) NOT NULL
        CHECK (method IN ('EMAIL_OTP', 'SMS_OTP', 'WHATSAPP_OTP', 'WEBAUTHN')),
    code_verifier VARCHAR(64),
    options_json TEXT,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mfa_challenges_expires_at ON mfa_challenges (expires_at);
CREATE INDEX idx_mfa_challenges_user_id ON mfa_challenges (user_id);

CREATE TABLE user_entities (
    id VARCHAR(1000) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200)
);

CREATE TABLE user_credentials (
    credential_id VARCHAR(1000) PRIMARY KEY,
    user_entity_user_id VARCHAR(1000) NOT NULL REFERENCES user_entities(id),
    public_key BYTEA NOT NULL,
    signature_count BIGINT,
    uv_initialized BOOLEAN,
    backup_eligible BOOLEAN NOT NULL,
    authenticator_transports VARCHAR(1000),
    public_key_credential_type VARCHAR(100),
    backup_state BOOLEAN NOT NULL,
    attestation_object BYTEA,
    attestation_client_data_json BYTEA,
    created TIMESTAMP,
    last_used TIMESTAMP,
    label VARCHAR(1000) NOT NULL
);

CREATE INDEX idx_user_credentials_user_id
    ON user_credentials (user_entity_user_id);

ALTER TABLE refresh_tokens
    ADD COLUMN authentication_methods VARCHAR(100) NOT NULL DEFAULT 'pwd';
