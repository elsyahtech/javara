CREATE TABLE user_compromised_password_hashes (
    user_id UUID NOT NULL REFERENCES users(id),
    password_hash VARCHAR(255) NOT NULL
);

CREATE INDEX idx_user_compromised_password_hashes_user_id
    ON user_compromised_password_hashes (user_id);
