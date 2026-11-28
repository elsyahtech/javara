WITH expired_tokens AS (
    SELECT token_hash
    FROM javara_refresh_tokens
    WHERE expires_at < :'backup_cutoff'::timestamptz
        - make_interval(days => :'token_grace_days'::integer)
    ORDER BY expires_at
    LIMIT 500
    FOR UPDATE SKIP LOCKED
),
deleted_tokens AS (
    DELETE FROM javara_refresh_tokens token
    USING expired_tokens expired
    WHERE token.token_hash = expired.token_hash
    RETURNING 1
)
SELECT count(*) FROM deleted_tokens;
