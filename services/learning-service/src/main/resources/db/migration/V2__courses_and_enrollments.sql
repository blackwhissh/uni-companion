CREATE TABLE courses (
    id uuid PRIMARY KEY,
    title varchar(200) NOT NULL,
    code varchar(32) NOT NULL,
    term varchar(32) NOT NULL,
    visibility varchar(32) NOT NULL,
    owner_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE enrollments (
    course_id uuid NOT NULL REFERENCES courses (id),
    user_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (course_id, user_id)
);
