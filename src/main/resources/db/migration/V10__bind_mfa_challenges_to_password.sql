ALTER TABLE mfa_challenges
    ADD COLUMN password_hash_digest VARCHAR(64);

UPDATE mfa_challenges
SET password_hash_digest = repeat('0', 64)
WHERE password_hash_digest IS NULL;

ALTER TABLE mfa_challenges
    ALTER COLUMN password_hash_digest SET NOT NULL;
