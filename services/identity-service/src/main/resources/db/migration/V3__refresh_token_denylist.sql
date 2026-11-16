CREATE TABLE refresh_token_denylist (
    jti varchar(128) PRIMARY KEY,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz NOT NULL
);

CREATE INDEX refresh_token_denylist_expires_idx
    ON refresh_token_denylist (expires_at);
