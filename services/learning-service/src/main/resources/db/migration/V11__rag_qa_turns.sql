CREATE TABLE rag_qa_turns (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    question text NOT NULL,
    answer text NOT NULL,
    citations_json text NOT NULL,
    material_ids_json text NOT NULL,
    retrieved_count integer NOT NULL,
    cited_count integer NOT NULL,
    insufficient boolean NOT NULL,
    latency_ms integer NOT NULL,
    follow_up boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    FOREIGN KEY (course_id, user_id) REFERENCES enrollments (course_id, user_id) ON DELETE CASCADE
);

CREATE INDEX rag_qa_turns_course_user_idx
    ON rag_qa_turns (course_id, user_id, created_at DESC);

CREATE INDEX rag_qa_turns_course_created_idx
    ON rag_qa_turns (course_id, created_at DESC);
