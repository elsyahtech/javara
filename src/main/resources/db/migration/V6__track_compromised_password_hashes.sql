CREATE TABLE javara_user_compromised_password_hashes (
    user_id UUID NOT NULL REFERENCES javara_users(id),
    password_hash VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, password_hash)
);

CREATE INDEX idx_user_compromised_password_hashes_user_id
    ON javara_user_compromised_password_hashes (user_id);
