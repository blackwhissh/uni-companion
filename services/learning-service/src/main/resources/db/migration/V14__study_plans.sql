CREATE TABLE study_plans (
    course_id uuid NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    exam_date date NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (course_id, user_id)
);
