create table simulator_webhook_delivery (
    delivery_id        varchar(64) primary key,
    seq                bigserial    not null,
    provider           varchar(32)  not null,
    test_run_id        varchar(64)  not null,
    merchant_id        varchar(128) not null,
    event_id           varchar(64)  not null,
    event_type         text,
    provider_object_id text,
    target_url         text         not null,
    content_type       text         not null,
    body               bytea        not null,
    sanitized_body     text         not null,
    retry_delays       text         not null,
    state              varchar(16)  not null,
    attempts_made      integer      not null,
    next_attempt_at    timestamptz,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null
);

create index simulator_webhook_delivery_by_run
    on simulator_webhook_delivery (provider, test_run_id, seq);

create index simulator_webhook_delivery_pending
    on simulator_webhook_delivery (state, next_attempt_at);

create table simulator_webhook_delivery_attempt (
    delivery_id       varchar(64) not null
        references simulator_webhook_delivery (delivery_id),
    attempt_number    integer     not null,
    attempted_at      timestamptz not null,
    verdict           varchar(32) not null,
    http_status       integer,
    transport_failure varchar(32),
    response_preview  text,
    primary key (delivery_id, attempt_number)
);

alter table simulator_audit_record
    alter column resolution_rule drop not null;
