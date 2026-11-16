ALTER TABLE rag_qa_turns
    ADD COLUMN embed_latency_ms integer NOT NULL DEFAULT 0,
    ADD COLUMN answer_latency_ms integer NOT NULL DEFAULT 0,
    ADD COLUMN model_available boolean NOT NULL DEFAULT true,
    ADD COLUMN retrieved_json text NOT NULL DEFAULT '[]';
