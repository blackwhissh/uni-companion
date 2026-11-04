ALTER TABLE match_opt_ins
    ADD COLUMN display_name varchar(120) NOT NULL DEFAULT '';

CREATE TABLE match_intents (
    course_id uuid NOT NULL,
    user_id uuid NOT NULL,
    intent varchar(32) NOT NULL,
    PRIMARY KEY (course_id, user_id, intent),
    FOREIGN KEY (course_id, user_id) REFERENCES match_opt_ins (course_id, user_id) ON DELETE CASCADE
);

CREATE INDEX match_intents_course_idx ON match_intents (course_id);
