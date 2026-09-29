CREATE TABLE users (
    id uuid PRIMARY KEY,
    email varchar(320) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    display_name varchar(120) NOT NULL,
    interests varchar(500),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE user_roles (
    user_id uuid NOT NULL REFERENCES users (id),
    role varchar(32) NOT NULL,
    PRIMARY KEY (user_id, role)
);
