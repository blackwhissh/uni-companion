ALTER TABLE flashcard_decks
    ADD COLUMN active boolean NOT NULL DEFAULT true,
    ADD COLUMN retired_at timestamptz,
    ADD COLUMN retired_reason varchar(500);

ALTER TABLE quizzes
    ADD COLUMN active boolean NOT NULL DEFAULT true,
    ADD COLUMN retired_at timestamptz,
    ADD COLUMN retired_reason varchar(500);

CREATE TABLE flashcard_deck_materials (
    deck_id uuid NOT NULL REFERENCES flashcard_decks (id) ON DELETE CASCADE,
    material_id uuid NOT NULL REFERENCES materials (id) ON DELETE CASCADE,
    PRIMARY KEY (deck_id, material_id)
);

CREATE INDEX flashcard_deck_materials_material_idx
    ON flashcard_deck_materials (material_id);

CREATE TABLE quiz_materials (
    quiz_id uuid NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    material_id uuid NOT NULL REFERENCES materials (id) ON DELETE CASCADE,
    PRIMARY KEY (quiz_id, material_id)
);

CREATE INDEX quiz_materials_material_idx
    ON quiz_materials (material_id);

CREATE TABLE study_generation_jobs (
    id uuid PRIMARY KEY,
    kind varchar(32) NOT NULL,
    course_id uuid NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    pool_key varchar(64) NOT NULL,
    requested_by uuid NOT NULL,
    status varchar(32) NOT NULL,
    version_id uuid,
    error varchar(500),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE UNIQUE INDEX study_generation_jobs_running_idx
    ON study_generation_jobs (kind, course_id, pool_key)
    WHERE status = 'RUNNING';

CREATE INDEX study_generation_jobs_pool_idx
    ON study_generation_jobs (kind, course_id, pool_key, created_at DESC);

CREATE TABLE study_delivery_events (
    id uuid PRIMARY KEY,
    kind varchar(32) NOT NULL,
    course_id uuid NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    pool_key varchar(64) NOT NULL,
    version_id uuid NOT NULL,
    outcome varchar(32) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX study_delivery_events_pool_idx
    ON study_delivery_events (kind, course_id, pool_key, created_at DESC);

CREATE TABLE study_version_reports (
    id uuid PRIMARY KEY,
    kind varchar(32) NOT NULL,
    version_id uuid NOT NULL,
    user_id uuid NOT NULL,
    reason varchar(500),
    created_at timestamptz NOT NULL,
    UNIQUE (kind, version_id, user_id)
);

CREATE INDEX study_version_reports_version_idx
    ON study_version_reports (kind, version_id);
