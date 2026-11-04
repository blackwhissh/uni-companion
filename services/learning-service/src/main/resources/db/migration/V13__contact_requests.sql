ALTER TABLE match_opt_ins
    ADD COLUMN contact_email varchar(320) NOT NULL DEFAULT '';

CREATE TABLE contact_requests (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL,
    requester_id uuid NOT NULL,
    recipient_id uuid NOT NULL,
    status varchar(16) NOT NULL,
    requester_name varchar(120) NOT NULL,
    recipient_name varchar(120) NOT NULL,
    requester_email varchar(320),
    recipient_email varchar(320),
    created_at timestamptz NOT NULL,
    decided_at timestamptz,
    CONSTRAINT contact_requests_pair UNIQUE (course_id, requester_id, recipient_id),
    CONSTRAINT contact_requests_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED'))
);

CREATE INDEX contact_requests_requester_idx ON contact_requests (course_id, requester_id);
CREATE INDEX contact_requests_recipient_idx ON contact_requests (course_id, recipient_id);
