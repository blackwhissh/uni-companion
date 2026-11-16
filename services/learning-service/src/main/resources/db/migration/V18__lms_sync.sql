CREATE TABLE course_lms_links (
    course_id uuid PRIMARY KEY REFERENCES courses (id) ON DELETE CASCADE,
    provider varchar(32) NOT NULL,
    external_course_id varchar(128) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    last_synced_at timestamptz,
    last_status varchar(32),
    last_message varchar(500),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX course_lms_links_provider_external_idx
    ON course_lms_links (provider, external_course_id);

ALTER TABLE materials
    ADD COLUMN source_provider varchar(32),
    ADD COLUMN source_external_id varchar(128),
    ADD COLUMN source_checksum varchar(128);

CREATE UNIQUE INDEX materials_course_source_uidx
    ON materials (course_id, source_provider, source_external_id)
    WHERE source_provider IS NOT NULL AND source_external_id IS NOT NULL;
