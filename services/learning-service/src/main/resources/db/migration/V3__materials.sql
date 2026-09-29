CREATE TABLE materials (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES courses (id),
    title varchar(200) NOT NULL,
    file_name varchar(255) NOT NULL,
    storage_path varchar(500) NOT NULL,
    visibility varchar(32) NOT NULL,
    processing_status varchar(32) NOT NULL,
    failure_reason varchar(500),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX materials_course_id_idx ON materials (course_id);

CREATE TABLE material_chunks (
    id uuid PRIMARY KEY,
    material_id uuid NOT NULL REFERENCES materials (id),
    page_number integer NOT NULL,
    chunk_index integer NOT NULL,
    content text NOT NULL
);

CREATE INDEX material_chunks_material_id_idx ON material_chunks (material_id);
