CREATE TABLE quiz_attempt_items (
    id uuid PRIMARY KEY,
    attempt_id uuid NOT NULL REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    prompt text NOT NULL,
    correct boolean NOT NULL,
    sort_order integer NOT NULL
);

CREATE INDEX quiz_attempt_items_attempt_idx ON quiz_attempt_items (attempt_id);
