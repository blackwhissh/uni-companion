ALTER TABLE quizzes
    ADD COLUMN practice_quiz boolean NOT NULL DEFAULT false;

CREATE TABLE flashcard_reviews (
    user_id uuid NOT NULL,
    card_id uuid NOT NULL,
    course_id uuid NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    front varchar(500) NOT NULL,
    status varchar(16) NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, card_id)
);

CREATE INDEX flashcard_reviews_course_idx
    ON flashcard_reviews (course_id, user_id, status);

CREATE TABLE study_activity (
    user_id uuid NOT NULL,
    course_id uuid NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    material_id uuid NOT NULL REFERENCES materials (id) ON DELETE CASCADE,
    kind varchar(16) NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, material_id, kind)
);

CREATE INDEX study_activity_course_idx
    ON study_activity (course_id, user_id);
