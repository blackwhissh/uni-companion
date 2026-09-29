ALTER TABLE flashcard_decks
    ADD COLUMN materials_key varchar(512) NOT NULL DEFAULT '';

ALTER TABLE quizzes
    ADD COLUMN materials_key varchar(512) NOT NULL DEFAULT '';

CREATE INDEX flashcard_decks_course_materials_idx
    ON flashcard_decks (course_id, materials_key, created_at DESC);

CREATE INDEX quizzes_course_materials_idx
    ON quizzes (course_id, materials_key, created_at DESC);
