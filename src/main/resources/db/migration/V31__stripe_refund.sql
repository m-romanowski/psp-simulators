create table stripe_refund (
    test_run_id varchar(64)  not null,
    refund_id   varchar(64)  not null,
    intent_id   varchar(64)  not null,
    account_id  varchar(128) not null,
    amount      text         not null,
    currency    text         not null,
    reason      varchar(32),
    status      varchar(32)  not null,
    origin      varchar(16)  not null,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    primary key (test_run_id, refund_id),
    constraint stripe_refund_amount_is_minor_units
        check (amount ~ '^[1-9][0-9]{0,17}$'),
    constraint stripe_refund_reason
        check (reason in ('duplicate', 'fraudulent', 'requested_by_customer',
                          'expired_uncaptured_charge')),
    constraint stripe_refund_status
        check (status in ('pending', 'requires_action', 'succeeded', 'failed', 'canceled')),
    constraint stripe_refund_is_not_the_payment_it_returns
        check (refund_id <> intent_id),
    foreign key (test_run_id, intent_id)
        references stripe_payment_intent (test_run_id, intent_id) on delete cascade
);

create index stripe_refund_by_intent on stripe_refund (test_run_id, intent_id);
