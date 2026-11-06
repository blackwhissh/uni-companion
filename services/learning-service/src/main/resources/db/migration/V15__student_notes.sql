CREATE TABLE student_notes (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    title varchar(160) NOT NULL,
    body text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX student_notes_course_user_idx
    ON student_notes (course_id, user_id, updated_at DESC);
