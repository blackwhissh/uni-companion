create table if not exists audit_events (
    id uuid primary key,
    actor_id uuid not null,
    action varchar(64) not null,
    resource_type varchar(64) not null,
    resource_id varchar(64) not null,
    details jsonb,
    correlation_id varchar(64),
    created_at timestamptz not null
);

create index if not exists audit_events_created_at_idx on audit_events (created_at desc);
create index if not exists audit_events_actor_idx on audit_events (actor_id, created_at desc);
