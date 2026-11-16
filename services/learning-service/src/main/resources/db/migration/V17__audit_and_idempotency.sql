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

create table if not exists idempotency_keys (
    user_id uuid not null,
    idempotency_key varchar(128) not null,
    route varchar(256) not null,
    request_fingerprint varchar(128) not null,
    status varchar(16) not null,
    http_status int,
    response_body text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (user_id, idempotency_key)
);

create index if not exists idempotency_keys_updated_at_idx on idempotency_keys (updated_at);
