create table tranzila_terminal (
    test_run_id                varchar(64)  not null,
    account_id                 varchar(128) not null,
    terminal_name              varchar(16)  not null,
    app_key                    text         not null,
    app_secret                 text         not null,
    access_token               varchar(48)  not null,
    authentication             varchar(40)  not null,
    verification_approval_code varchar(3)   not null,
    j5_validity                text         not null,
    primary key (test_run_id, account_id),
    constraint tranzila_terminal_name_is_the_published_shape
        check (terminal_name ~ '^[a-z][a-z0-9]{2,15}$'),
    constraint tranzila_terminal_access_token
        check (access_token in ('AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT',
                                'AS_THE_QUICKSTART_SIGNS_IT')),
    constraint tranzila_terminal_authentication
        check (authentication in ('THE_ACCESS_TOKEN_IS_VERIFIED',
                                  'THE_ACCESS_TOKEN_IS_NOT_VERIFIED')),
    constraint tranzila_terminal_verification_approval_code
        check (verification_approval_code in ('777', '000')),
    constraint tranzila_terminal_j5_validity_is_a_span_a_hold_can_outlive
        check (case
                   when j5_validity ~
                        '^P([0-9]{1,3}D)?(T(?=[0-9])([0-9]{1,5}H)?([0-9]{1,7}M)?([0-9]{1,9}(\.[0-9]{1,6})?S)?)?$'
                       and j5_validity ~ '[0-9]'
                       then j5_validity::interval > interval '0'
                           and j5_validity::interval <= interval '365 days'
                   else false
               end)
);

create unique index tranzila_terminal_by_terminal_name
    on tranzila_terminal (test_run_id, terminal_name);

create unique index tranzila_terminal_by_app_key on tranzila_terminal (test_run_id, app_key);

create index tranzila_terminal_by_credentials on tranzila_terminal (app_key);

create table tranzila_nonce (
    test_run_id varchar(64)  not null,
    account_id  varchar(128) not null,
    nonce       text         not null,
    seen_at     timestamptz  not null,
    primary key (test_run_id, account_id, nonce),
    constraint tranzila_nonce_is_not_empty
        check (char_length(nonce) > 0)
);

create table tranzila_transaction (
    test_run_id             varchar(64)  not null,
    transaction_id          bigint       not null,
    root_txn_id             bigint       not null,
    account_id              varchar(128) not null,
    terminal_name           varchar(16)  not null,
    txn_type                varchar(8)   not null,
    verify_mode             smallint,
    reference_txn_id        bigint,
    processor_response_code varchar(3)   not null,
    amount                  text         not null,
    currency_code           varchar(3)   not null,
    auth_number             varchar(7),
    token                   varchar(64),
    card_mask               varchar(32)  not null,
    card_type               smallint     not null,
    expiry_month            smallint     not null,
    expiry_year             smallint     not null,
    response_cvv            varchar(1)   not null,
    response_id             varchar(1)   not null,
    dc_disable              varchar(254),
    hold_expires_at         timestamptz,
    origin                  varchar(16)  not null,
    created_at              timestamptz  not null,
    arrival                 bigint       not null default nextval('simulator_provider_object_arrival'),
    primary key (test_run_id, transaction_id),
    constraint tranzila_transaction_id_is_positive
        check (transaction_id > 0),
    constraint tranzila_transaction_type
        check (txn_type in ('debit', 'verify', 'force', 'reversal', 'credit', 'cancel')),
    constraint tranzila_transaction_verify_mode_belongs_to_a_verify
        check ((txn_type = 'verify') = (verify_mode is not null)
               and (verify_mode is null or verify_mode in (2, 5))),
    constraint tranzila_transaction_an_original_references_nothing
        check ((txn_type in ('debit', 'verify')) = (reference_txn_id is null)
               and (txn_type in ('debit', 'verify')) = (root_txn_id = transaction_id)),
    constraint tranzila_transaction_processor_code_is_three_digits
        check (processor_response_code ~ '^[0-9]{3}$'),
    constraint tranzila_transaction_an_approval_carries_an_authorization_number
        check ((processor_response_code in ('000', '777')) = (auth_number is not null)),
    constraint tranzila_transaction_amount_is_a_decimal
        check (amount ~ '^[0-9]+(\.[0-9]+)?$'),
    constraint tranzila_transaction_currency
        check (currency_code in ('ILS', 'USD', 'EUR', 'GBP')),
    constraint tranzila_transaction_token_is_alphanumeric
        check (token is null or token ~ '^[0-9A-Za-z]+$'),
    constraint tranzila_transaction_card_type
        check (card_type between 1 and 6),
    constraint tranzila_transaction_expiry
        check (expiry_month between 1 and 12 and expiry_year between 0 and 99),
    constraint tranzila_transaction_response_cvv
        check (response_cvv in ('0', '1', '2', '3')),
    constraint tranzila_transaction_response_id
        check (response_id in ('0', '1', '2', '3')),
    constraint tranzila_transaction_an_approved_j5_expires
        check ((txn_type = 'verify' and verify_mode = 5 and auth_number is not null)
               = (hold_expires_at is not null)
               and (hold_expires_at is null or hold_expires_at > created_at))
);

create unique index tranzila_transaction_by_arrival on tranzila_transaction (test_run_id, arrival);

create unique index tranzila_transaction_closes_a_hold_once
    on tranzila_transaction (test_run_id, reference_txn_id)
    where txn_type in ('force', 'reversal') and processor_response_code = '000';

create unique index tranzila_transaction_cancels_once
    on tranzila_transaction (test_run_id, reference_txn_id)
    where txn_type = 'cancel' and processor_response_code = '000';

create index tranzila_transaction_by_root on tranzila_transaction (test_run_id, root_txn_id);

create index tranzila_transaction_by_token on tranzila_transaction (test_run_id, account_id, token);

create index tranzila_transaction_by_dc_disable
    on tranzila_transaction (test_run_id, account_id, dc_disable);

create table tranzila_keyed_exchange (
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
    constraint tranzila_keyed_exchange_verdict
        check (verdict in ('FIRST_REQUEST', 'DUPLICATE_KEY_REFUSAL', 'REPEAT_ADMITTED',
                           'CORRELATED_RETRY')),
    constraint tranzila_keyed_exchange_answers_in_full_or_not_at_all
        check ((http_status is null and content_type is null and body is null)
               or (http_status is not null and content_type is not null and body is not null))
);

create index tranzila_keyed_exchange_newest_first
    on tranzila_keyed_exchange (test_run_id, arrival desc)
    where seq = 0;
