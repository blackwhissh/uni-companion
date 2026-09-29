CREATE TABLE match_opt_ins (
    course_id uuid NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (course_id, user_id)
);

CREATE INDEX match_opt_ins_course_id_idx ON match_opt_ins (course_id);
