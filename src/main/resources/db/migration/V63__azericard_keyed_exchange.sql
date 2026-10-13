create table azericard_keyed_exchange (
    arrival            bigint       generated always as identity,
    test_run_id        varchar(64)  not null,
    account_id         varchar(128) not null,
    key_name           varchar(64)  not null,
    key_value          varchar(255) not null,
    seq                integer      not null,
    operation          varchar(64)  not null,
    fingerprint        varchar(64)  not null,
    verdict            varchar(32)  not null,
    http_status        integer,
    content_type       text,
    body               text,
    provider_object_id varchar(64),
    state_changed      boolean      not null,
    at                 timestamptz  not null,
    primary key (test_run_id, account_id, key_name, key_value, seq),
    unique (arrival),
    constraint azericard_keyed_exchange_verdict
        check (verdict in ('FIRST_REQUEST', 'DUPLICATE_KEY_REFUSAL', 'REPEAT_ADMITTED',
                           'CORRELATED_RETRY')),
    constraint azericard_keyed_exchange_answers_in_full_or_not_at_all
        check ((http_status is null and content_type is null and body is null)
               or (http_status is not null and content_type is not null and body is not null))
);

create index azericard_keyed_exchange_newest_first
    on azericard_keyed_exchange (test_run_id, arrival desc)
    where seq = 0;
