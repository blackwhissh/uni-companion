CREATE TABLE study_chat_sessions (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    material_ids jsonb NOT NULL,
    language varchar(2),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX study_chat_sessions_course_user_idx
    ON study_chat_sessions (course_id, user_id, updated_at DESC);

CREATE TABLE study_chat_messages (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES study_chat_sessions (id) ON DELETE CASCADE,
    role varchar(16) NOT NULL,
    content text NOT NULL,
    citations_json jsonb,
    created_at timestamptz NOT NULL
);

CREATE INDEX study_chat_messages_session_idx
    ON study_chat_messages (session_id, created_at ASC);
