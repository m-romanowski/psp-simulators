create table worldline_merchant (
    test_run_id         varchar(64)  not null,
    account_id          varchar(128) not null,
    merchant_id         varchar(128) not null,
    api_key             text         not null,
    api_secret          text         not null,
    signed_path_version varchar(64)  not null,
    capture_landing     varchar(32)  not null,
    authentication      varchar(32)  not null,
    primary key (test_run_id, account_id),
    constraint worldline_merchant_id_is_one_path_segment
        check (merchant_id ~ '^[^/?#]+$'),
    constraint worldline_merchant_signed_path_version
        check (signed_path_version in ('V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT',
                                       'V1_AS_THE_WORKED_EXAMPLES_SIGN_IT')),
    constraint worldline_merchant_capture_landing
        check (capture_landing in ('CAPTURED_9', 'CAPTURE_REQUESTED_91', 'PENDING_CAPTURE_9')),
    constraint worldline_merchant_authentication
        check (authentication in ('THE_SIGNATURE_IS_VERIFIED', 'THE_SIGNATURE_IS_NOT_VERIFIED'))
);

create unique index worldline_merchant_by_merchant_id
    on worldline_merchant (test_run_id, merchant_id);

create unique index worldline_merchant_by_api_key on worldline_merchant (test_run_id, api_key);

create index worldline_merchant_by_credentials on worldline_merchant (api_key);

create table worldline_hosted_tokenization (
    test_run_id             varchar(64)  not null,
    hosted_tokenization_id  varchar(64)  not null,
    account_id              varchar(128) not null,
    merchant_id             varchar(128) not null,
    native_status           varchar(64)  not null,
    hosted_tokenization_url text         not null,
    partial_redirect_url    text         not null,
    token                   varchar(64),
    card_number             varchar(32),
    expiry_date             varchar(8),
    expires_at              timestamptz  not null,
    origin                  varchar(16)  not null,
    created_at              timestamptz  not null,
    updated_at              timestamptz  not null,
    arrival                 bigint       not null default nextval('simulator_provider_object_arrival'),
    primary key (test_run_id, hosted_tokenization_id),
    constraint worldline_hosted_tokenization_id_is_thirty_two_lowercase_hex
        check (hosted_tokenization_id ~ '^[0-9a-f]{32}$'),
    constraint worldline_hosted_tokenization_expires_after_it_was_created
        check (expires_at > created_at),
    constraint worldline_hosted_tokenization_carries_a_card_with_its_token
        check ((token is null and card_number is null and expiry_date is null)
               or (token is not null and card_number is not null and expiry_date is not null)),
    constraint worldline_hosted_tokenization_holds_a_token_only_once_created
        check (token is null or native_status = 'CREATED')
);

create unique index worldline_hosted_tokenization_by_arrival
    on worldline_hosted_tokenization (test_run_id, arrival);

create unique index worldline_hosted_tokenization_by_token
    on worldline_hosted_tokenization (test_run_id, token);

create table worldline_payment (
    test_run_id            varchar(64)  not null,
    payment_id             varchar(64)  not null,
    account_id             varchar(128) not null,
    merchant_id            varchar(128) not null,
    merchant_reference     varchar(40),
    merchant_customer_id   varchar(255),
    token                  varchar(64),
    hosted_tokenization_id varchar(64),
    card_number            varchar(32),
    expiry_date            varchar(8),
    amount                 bigint       not null,
    currency_code          varchar(3)   not null,
    authorization_mode     varchar(32)  not null,
    native_status          varchar(64)  not null,
    status_category        varchar(64)  not null,
    status_code            integer      not null,
    authorization_closed   boolean      not null,
    origin                 varchar(16)  not null,
    created_at             timestamptz  not null,
    updated_at             timestamptz  not null,
    arrival                bigint       not null default nextval('simulator_provider_object_arrival'),
    primary key (test_run_id, payment_id),
    constraint worldline_payment_id_is_payid_payidsub
        check (payment_id ~ '^[0-9]{10}_[0-9]+$'),
    constraint worldline_payment_amount_is_within_the_published_bounds
        check (amount between 0 and 999999999999),
    constraint worldline_payment_currency_is_three_characters
        check (char_length(currency_code) = 3),
    constraint worldline_payment_authorization_mode
        check (authorization_mode in ('PRE_AUTHORIZATION', 'FINAL_AUTHORIZATION', 'SALE'))
);

create unique index worldline_payment_by_arrival on worldline_payment (test_run_id, arrival);

create index worldline_payment_by_reference
    on worldline_payment (test_run_id, merchant_id, merchant_reference);

create table worldline_payment_identifier (
    test_run_id varchar(64) not null,
    identifier  varchar(64) not null,
    payment_id  varchar(64) not null,
    payid_sub   integer     not null,
    minted_at   timestamptz not null,
    primary key (test_run_id, identifier),
    constraint worldline_payment_identifier_is_payid_payidsub
        check (identifier ~ '^[0-9]{10}_[0-9]+$'),
    constraint worldline_payment_identifier_counts_from_zero
        check (payid_sub >= 0)
);

create unique index worldline_payment_identifier_once_per_operation
    on worldline_payment_identifier (test_run_id, payment_id, payid_sub);

create table worldline_payment_operation (
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
        references worldline_payment (test_run_id, payment_id) on delete cascade
);

create table worldline_idempotence_key (
    test_run_id        varchar(64)  not null,
    account_id         varchar(128) not null,
    key_value          varchar(40)  not null,
    header_name        varchar(64)  not null,
    operation          varchar(64)  not null,
    fingerprint        varchar(64)  not null,
    claim_state        varchar(32)  not null,
    http_status        integer,
    content_type       text,
    body               text,
    provider_object_id varchar(64),
    claimed_at         timestamptz  not null,
    primary key (test_run_id, account_id, key_value),
    constraint worldline_idempotence_key_claim_state
        check (claim_state in ('RUNNING', 'COMPLETED')),
    constraint worldline_idempotence_key_answers_in_full_or_not_at_all
        check ((claim_state = 'RUNNING'
                and http_status is null and content_type is null and body is null)
               or (claim_state = 'COMPLETED'
                   and http_status is not null and content_type is not null and body is not null))
);

create table worldline_keyed_exchange (
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
    constraint worldline_keyed_exchange_verdict
        check (verdict in ('FIRST_REQUEST', 'TRUE_IDEMPOTENT_REPLAY', 'CORRELATED_RETRY',
                           'REFUSED_WHILE_THE_KEY_WAS_HELD')),
    constraint worldline_keyed_exchange_answers_in_full_or_not_at_all
        check ((http_status is null and content_type is null and body is null)
               or (http_status is not null and content_type is not null and body is not null))
);

create index worldline_keyed_exchange_newest_first
    on worldline_keyed_exchange (test_run_id, arrival desc)
    where seq = 0;
