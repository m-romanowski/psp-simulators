create table tranzila_challenge (
    track_id              varchar(48)  not null,
    test_run_id           varchar(64)  not null,
    account_id            varchar(128) not null,
    terminal_name         varchar(16)  not null,
    transaction_id        bigint       not null,
    txn_type              varchar(8)   not null,
    verify_mode           smallint,
    amount                text         not null,
    currency_code         varchar(3)   not null,
    token                 varchar(64)  not null,
    card_mask             varchar(32)  not null,
    card_type             smallint     not null,
    expiry_month          smallint     not null,
    expiry_year           smallint     not null,
    response_cvv          varchar(1)   not null,
    response_id           varchar(1)   not null,
    dc_disable            varchar(254),
    force_txn_on_3ds_fail boolean      not null,
    original_request      text         not null,
    redirect_url          text,
    redirect_params       text         not null,
    hosted_fields         text,
    hosted_form_data      text,
    origin                varchar(16)  not null,
    created_at            timestamptz  not null,
    payer_verdict         varchar(8),
    answered_at           timestamptz,
    completed_at          timestamptz,
    arrival               bigint       not null default nextval('simulator_provider_object_arrival'),
    primary key (track_id),
    constraint tranzila_challenge_track_id_is_the_simulators_shape
        check (track_id ~ '^3ds_[0-9a-f]{40}$'),
    constraint tranzila_challenge_transaction_id_is_positive
        check (transaction_id > 0),
    constraint tranzila_challenge_is_an_original
        check (txn_type in ('debit', 'verify')
               and (txn_type = 'verify') = (verify_mode is not null)
               and (verify_mode is null or verify_mode in (2, 5))),
    constraint tranzila_challenge_amount_is_a_decimal
        check (amount ~ '^[0-9]+(\.[0-9]+)?$'),
    constraint tranzila_challenge_currency
        check (currency_code in ('ILS', 'USD', 'EUR', 'GBP')),
    constraint tranzila_challenge_token_is_alphanumeric
        check (token ~ '^[0-9A-Za-z]+$'),
    constraint tranzila_challenge_card_type
        check (card_type between 1 and 6),
    constraint tranzila_challenge_expiry
        check (expiry_month between 1 and 12 and expiry_year between 0 and 99),
    constraint tranzila_challenge_response_cvv
        check (response_cvv in ('0', '1', '2', '3')),
    constraint tranzila_challenge_response_id
        check (response_id in ('0', '1', '2', '3')),
    constraint tranzila_challenge_payer_verdict
        check (payer_verdict is null or payer_verdict in ('PASSED', 'FAILED')),
    constraint tranzila_challenge_a_verdict_carries_its_moment
        check ((payer_verdict is null) = (answered_at is null)),
    constraint tranzila_challenge_completes_after_the_payer_answered
        check (completed_at is null or payer_verdict is not null),
    constraint tranzila_challenge_hosted_fields_carry_their_form
        check ((hosted_fields is null) = (hosted_form_data is null))
);

create unique index tranzila_challenge_by_arrival on tranzila_challenge (test_run_id, arrival);
