DELETE FROM material_chunks;

ALTER TABLE material_chunks
    ADD COLUMN embedding vector(768) NOT NULL;
