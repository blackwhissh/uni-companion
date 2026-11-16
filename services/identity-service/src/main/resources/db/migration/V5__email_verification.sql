ALTER TABLE users
    ADD COLUMN email_verified boolean NOT NULL DEFAULT false,
    ADD COLUMN email_verified_at timestamptz;

CREATE TABLE email_verifications (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email varchar(320) NOT NULL,
    code_hash varchar(64) NOT NULL,
    attempt_count integer NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL
);

CREATE INDEX email_verifications_user_idx
    ON email_verifications (user_id, created_at DESC);
