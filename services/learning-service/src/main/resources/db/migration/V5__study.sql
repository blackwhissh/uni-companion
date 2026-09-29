CREATE TABLE flashcard_decks (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES courses (id),
    user_id uuid NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX flashcard_decks_course_user_idx ON flashcard_decks (course_id, user_id, created_at DESC);

CREATE TABLE flashcards (
    id uuid PRIMARY KEY,
    deck_id uuid NOT NULL REFERENCES flashcard_decks (id) ON DELETE CASCADE,
    front varchar(500) NOT NULL,
    back text NOT NULL,
    sort_order integer NOT NULL
);

CREATE INDEX flashcards_deck_id_idx ON flashcards (deck_id);

CREATE TABLE quizzes (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES courses (id),
    user_id uuid NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX quizzes_course_user_idx ON quizzes (course_id, user_id, created_at DESC);

CREATE TABLE quiz_questions (
    id uuid PRIMARY KEY,
    quiz_id uuid NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    prompt text NOT NULL,
    options_json text NOT NULL,
    correct_index integer NOT NULL,
    sort_order integer NOT NULL
);

CREATE INDEX quiz_questions_quiz_id_idx ON quiz_questions (quiz_id);

CREATE TABLE quiz_attempts (
    id uuid PRIMARY KEY,
    quiz_id uuid NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    score integer NOT NULL,
    total integer NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX quiz_attempts_quiz_user_idx ON quiz_attempts (quiz_id, user_id, created_at DESC);
