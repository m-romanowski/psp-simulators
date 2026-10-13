create table autra_merchant (
    test_run_id                varchar(64)  not null,
    account_id                 varchar(128) not null,
    client_id                  text         not null,
    client_secret              text         not null,
    document_id                varchar(32)  not null,
    oauth_failure              varchar(32)  not null,
    oauth_field_casing         varchar(32)  not null,
    pre_authorization_shape    varchar(32)  not null,
    void_shape                 varchar(32)  not null,
    authorizer_rejected_message text        not null,
    merchant_not_found_status  varchar(32)  not null,
    primary key (test_run_id, account_id),
    constraint autra_merchant_document_is_digits_only
        check (document_id ~ '^[0-9]+$'),
    constraint autra_merchant_oauth_failure
        check (oauth_failure in ('BAD_REQUEST_WITH_ERROR_STRING', 'UNAUTHORIZED_INVALID_CLIENT')),
    constraint autra_merchant_oauth_field_casing
        check (oauth_field_casing in ('CAMEL_CASE', 'SNAKE_CASE')),
    constraint autra_merchant_pre_authorization_shape
        check (pre_authorization_shape in ('PUBLISHED_SCHEMA', 'PUBLISHED_GUIDE_EXAMPLE')),
    constraint autra_merchant_void_shape
        check (void_shape in ('PUBLISHED_SCHEMA', 'PUBLISHED_GUIDE_EXAMPLE')),
    constraint autra_merchant_authorizer_rejected_message
        check (authorizer_rejected_message ~ '^[0-9]{2} - .+$'),
    constraint autra_merchant_not_found_status
        check (merchant_not_found_status in ('FORBIDDEN', 'NOT_FOUND', 'UNPROCESSABLE'))
);

create unique index autra_merchant_by_client_id on autra_merchant (test_run_id, client_id);

create unique index autra_merchant_by_document_id on autra_merchant (test_run_id, document_id);

create index autra_merchant_by_credentials on autra_merchant (client_id);

create table autra_access_token (
    token       varchar(512) primary key,
    test_run_id varchar(64)  not null,
    account_id  varchar(128) not null,
    issued_at   timestamptz  not null,
    expires_at  timestamptz  not null,
    constraint autra_access_token_expires_after_it_is_issued
        check (expires_at > issued_at)
);

create index autra_access_token_by_run on autra_access_token (test_run_id);

create table autra_acquiring_session (
    test_run_id         varchar(64)  not null,
    transactional_token varchar(512) not null,
    account_id          varchar(128) not null,
    document_id         varchar(32)  not null,
    created_at          timestamptz  not null,
    primary key (test_run_id, transactional_token)
);

create table autra_payment (
    test_run_id       varchar(64)  not null,
    payment_id        varchar(64)  not null,
    account_id        varchar(128) not null,
    document_id       varchar(32)  not null,
    order_id          varchar(255),
    transaction_type  varchar(16)  not null,
    amount            text         not null,
    currency          text         not null,
    installments      integer      not null,
    native_status     varchar(64)  not null,
    nsu               varchar(32)  not null,
    authorization_code varchar(32) not null,
    rrn               varchar(32)  not null,
    tracking_number   varchar(32)  not null,
    slug_token        varchar(255),
    hold_released_at  timestamptz,
    origin            varchar(16)  not null,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    arrival           bigint       not null default nextval('simulator_provider_object_arrival'),
    primary key (test_run_id, payment_id),
    constraint autra_payment_installments_are_one_to_twelve
        check (installments between 1 and 12)
);

create unique index autra_payment_by_arrival on autra_payment (test_run_id, arrival);

create index autra_payment_by_order on autra_payment (test_run_id, document_id, order_id);

create table autra_payment_operation (
    test_run_id          varchar(64) not null,
    payment_id           varchar(64) not null,
    seq                  integer     not null,
    kind                 varchar(32) not null,
    amount               text        not null,
    closes_authorization boolean,
    created_object_id    varchar(64),
    native_status        varchar(64) not null,
    normalized_status    varchar(32) not null,
    created_at           timestamptz not null,
    primary key (test_run_id, payment_id, seq),
    foreign key (test_run_id, payment_id)
        references autra_payment (test_run_id, payment_id) on delete cascade
);

create table autra_payment_id_claim (
    test_run_id varchar(64) not null,
    payment_id  varchar(64) not null,
    primary key (test_run_id, payment_id)
);

create table autra_order_claim (
    test_run_id varchar(64)  not null,
    document_id varchar(32)  not null,
    order_id    varchar(255) not null,
    account_id  varchar(128) not null,
    claim_state varchar(64)  not null,
    payment_id  varchar(64),
    at          timestamptz  not null,
    primary key (test_run_id, document_id, order_id),
    constraint autra_order_claim_state
        check (claim_state in ('HELD_BEFORE_THE_ACQUIRER_WAS_CALLED', 'PRE_AUTHORIZED')),
    constraint autra_order_claim_names_the_payment_that_holds_it
        check ((claim_state = 'HELD_BEFORE_THE_ACQUIRER_WAS_CALLED' and payment_id is null)
               or (claim_state = 'PRE_AUTHORIZED' and payment_id is not null))
);

create table autra_keyed_exchange (
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
    constraint autra_keyed_exchange_verdict
        check (verdict in ('FIRST_REQUEST', 'DUPLICATE_KEY_REFUSAL', 'REPEAT_ADMITTED',
                           'CORRELATED_RETRY', 'REFUSED_WHILE_THE_KEY_WAS_HELD')),
    constraint autra_keyed_exchange_answers_in_full_or_not_at_all
        check ((http_status is null and content_type is null and body is null)
               or (http_status is not null and content_type is not null and body is not null))
);

create index autra_keyed_exchange_newest_first
    on autra_keyed_exchange (test_run_id, arrival desc)
    where seq = 0;
