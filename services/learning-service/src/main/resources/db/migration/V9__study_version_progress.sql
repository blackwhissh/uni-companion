CREATE TABLE flashcard_deck_progress (
    course_id uuid NOT NULL,
    user_id uuid NOT NULL,
    materials_key varchar(512) NOT NULL,
    deck_id uuid NOT NULL REFERENCES flashcard_decks (id),
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (course_id, user_id, materials_key)
);

CREATE INDEX flashcard_deck_progress_deck_idx ON flashcard_deck_progress (deck_id);

CREATE TABLE quiz_progress (
    course_id uuid NOT NULL,
    user_id uuid NOT NULL,
    materials_key varchar(512) NOT NULL,
    quiz_id uuid NOT NULL REFERENCES quizzes (id),
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (course_id, user_id, materials_key)
);

CREATE INDEX quiz_progress_quiz_idx ON quiz_progress (quiz_id);
