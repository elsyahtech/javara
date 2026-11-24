ALTER TABLE refresh_tokens
    ADD COLUMN session_id UUID,
    ADD COLUMN session_started_at TIMESTAMPTZ,
    ADD COLUMN last_activity_at TIMESTAMPTZ,
    ADD COLUMN session_expires_at TIMESTAMPTZ;

UPDATE refresh_tokens
SET session_id = gen_random_uuid(),
    session_started_at = created_at,
    last_activity_at = created_at,
    session_expires_at = LEAST(expires_at, created_at + INTERVAL '12 hours');

ALTER TABLE refresh_tokens
    ALTER COLUMN session_id SET NOT NULL,
    ALTER COLUMN session_started_at SET NOT NULL,
    ALTER COLUMN last_activity_at SET NOT NULL,
    ALTER COLUMN session_expires_at SET NOT NULL;

CREATE INDEX idx_refresh_tokens_session_id ON refresh_tokens (session_id);
