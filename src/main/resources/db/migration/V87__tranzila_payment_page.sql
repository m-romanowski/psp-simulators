create table tranzila_handshake (
    thtk           varchar(42)  not null,
    test_run_id    varchar(64)  not null,
    account_id     varchar(128) not null,
    terminal_name  varchar(16)  not null,
    sum            text         not null,
    request_params text,
    created_at     timestamptz  not null,
    expires_at     timestamptz  not null,
    primary key (thtk),
    constraint tranzila_handshake_thtk_is_the_published_shape
        check (thtk ~ '^w[0-9a-f]{41}$'),
    constraint tranzila_handshake_sum_is_a_decimal
        check (sum ~ '^[0-9]+(\.[0-9]+)?$'),
    constraint tranzila_handshake_expires_after_it_was_made
        check (expires_at > created_at)
);

create index tranzila_handshake_of_a_run on tranzila_handshake (test_run_id);

create table tranzila_page (
    page_id        varchar(40)  not null,
    test_run_id    varchar(64)  not null,
    account_id     varchar(128) not null,
    terminal_name  varchar(16)  not null,
    sum            text         not null,
    currency       varchar(3)   not null,
    tranmode       varchar(2)   not null,
    success_url    text,
    fail_url       text,
    notify_url     text,
    dc_disable     varchar(254),
    thtk           varchar(42),
    request_params text,
    echoed         text         not null,
    origin         varchar(16)  not null,
    created_at     timestamptz  not null,
    outcome        varchar(16),
    transaction_id bigint,
    response       varchar(3),
    token          varchar(64),
    card_mask      varchar(32),
    card_type      smallint,
    settled_at     timestamptz,
    arrival        bigint       not null default nextval('simulator_provider_object_arrival'),
    primary key (page_id),
    constraint tranzila_page_id_is_the_simulators_shape
        check (page_id ~ '^directng_[0-9a-f]{24}$'),
    constraint tranzila_page_sum_is_a_decimal
        check (sum ~ '^[0-9]+(\.[0-9]+)?$'),
    constraint tranzila_page_currency
        check (currency in ('1', '2', '978', '826')),
    constraint tranzila_page_tranmode
        check (tranmode in ('A', 'V', 'N', 'K', 'AK', 'VK', 'NK')),
    constraint tranzila_page_outcome
        check (outcome is null or outcome in ('PAID', 'DECLINED', 'ABANDONED')),
    constraint tranzila_page_settles_once_with_its_moment
        check ((outcome is null) = (settled_at is null)),
    constraint tranzila_page_a_token_only_page_records_no_transaction
        check (tranmode <> 'K' or transaction_id is null),
    constraint tranzila_page_token_is_alphanumeric
        check (token is null or token ~ '^[0-9A-Za-z]+$'),
    constraint tranzila_page_response_is_three_digits
        check (response is null or response ~ '^[0-9]{3}$'),
    constraint tranzila_page_card_type
        check (card_type is null or card_type between 1 and 6)
);

create unique index tranzila_page_by_arrival on tranzila_page (test_run_id, arrival);

create index tranzila_page_by_transaction on tranzila_page (test_run_id, transaction_id);
