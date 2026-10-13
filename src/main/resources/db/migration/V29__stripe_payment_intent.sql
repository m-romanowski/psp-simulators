create table stripe_payment_intent (
    test_run_id         varchar(64)  not null,
    intent_id           varchar(64)  not null,
    account_id          varchar(128) not null,
    amount              text         not null,
    currency            text         not null,
    capture_method      varchar(16)  not null,
    status              varchar(32)  not null,
    payment_method      varchar(128),
    customer            varchar(128),
    receipt_email       text,
    client_secret       text         not null,
    last_payment_error  varchar(32),
    cancellation_reason varchar(32),
    capture_expires_at  timestamptz  not null,
    origin              varchar(16)  not null,
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null,
    primary key (test_run_id, intent_id),
    constraint stripe_payment_intent_amount_is_minor_units
        check (amount ~ '^[1-9][0-9]{0,17}$'),
    constraint stripe_payment_intent_capture_method
        check (capture_method in ('automatic', 'automatic_async', 'manual')),
    constraint stripe_payment_intent_status
        check (status in ('requires_payment_method', 'requires_confirmation', 'requires_action',
                          'processing', 'requires_capture', 'succeeded', 'canceled')),
    constraint stripe_payment_intent_last_payment_error
        check (last_payment_error in ('generic_decline', 'insufficient_funds', 'expired_card',
                                      'incorrect_cvc', 'processing_error',
                                      'authentication_required')),
    constraint stripe_payment_intent_cancellation_reason
        check (cancellation_reason in ('duplicate', 'fraudulent', 'requested_by_customer',
                                       'abandoned', 'expired'))
);

create index stripe_payment_intent_by_account on stripe_payment_intent (test_run_id, account_id);

create table stripe_payment_intent_operation (
    test_run_id          varchar(64) not null,
    intent_id            varchar(64) not null,
    seq                  integer     not null,
    kind                 varchar(32) not null,
    amount               text        not null,
    closes_authorization boolean,
    created_object_id    varchar(64),
    native_status        varchar(32) not null,
    normalized_status    varchar(32) not null,
    created_at           timestamptz not null,
    primary key (test_run_id, intent_id, seq),
    constraint stripe_operation_names_another_object
        check (created_object_id is null or created_object_id <> intent_id),
    foreign key (test_run_id, intent_id)
        references stripe_payment_intent (test_run_id, intent_id) on delete cascade
);

create table stripe_intent_id_claim (
    test_run_id varchar(64) not null,
    intent_id   varchar(64) not null,
    primary key (test_run_id, intent_id)
);

create table stripe_idempotency_key (
    test_run_id        varchar(64)  not null,
    account_id         varchar(128) not null,
    idempotency_key    varchar(255) not null,
    fingerprint        varchar(64)  not null,
    in_progress        boolean      not null,
    http_status        integer,
    content_type       text,
    body               text,
    outcome            varchar(16),
    provider_object_id varchar(64),
    created_at         timestamptz  not null,
    primary key (test_run_id, account_id, idempotency_key),
    constraint stripe_idempotency_key_holds_an_answer_only_once_it_has_one
        check ((in_progress and http_status is null and content_type is null and body is null
                and outcome is null)
               or (not in_progress and http_status is not null and content_type is not null
                   and body is not null and outcome is not null))
);
