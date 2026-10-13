create table stripe_payment_method (
    test_run_id       varchar(64)  not null,
    payment_method_id varchar(128) not null,
    account_id        varchar(128) not null,
    customer_id       varchar(128),
    method_type       varchar(16)  not null,
    card_brand        varchar(32),
    card_last4        varchar(4),
    card_exp_month    integer,
    card_exp_year     integer,
    card_funding      varchar(32),
    detached_at       timestamptz,
    origin            varchar(16)  not null,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    arrival           bigint       not null default nextval('simulator_provider_object_arrival'),
    primary key (test_run_id, payment_method_id),
    constraint stripe_payment_method_type
        check (method_type in ('CARD', 'PAYPAL')),
    constraint stripe_payment_method_card_shape check (
        (method_type = 'CARD'
            and card_brand is not null
            and card_last4 is not null
            and card_exp_month is not null
            and card_exp_year is not null
            and card_funding is not null)
        or (method_type <> 'CARD'
            and card_brand is null
            and card_last4 is null
            and card_exp_month is null
            and card_exp_year is null
            and card_funding is null)),
    constraint stripe_payment_method_last4
        check (card_last4 is null or card_last4 ~ '^[0-9]{4}$'),
    constraint stripe_payment_method_detachment_is_permanent
        check (detached_at is null or customer_id is null),
    constraint stripe_payment_method_is_not_a_payment_intent
        check (payment_method_id not like 'pi/_sim/_%' escape '/'),
    constraint stripe_payment_method_is_not_a_refund
        check (payment_method_id not like 're/_sim/_%' escape '/'),
    constraint stripe_payment_method_is_not_a_customer
        check (payment_method_id not like 'cus/_sim/_%' escape '/')
);

create unique index stripe_payment_method_by_arrival
    on stripe_payment_method (test_run_id, arrival);

create index stripe_payment_method_by_customer
    on stripe_payment_method (test_run_id, account_id, customer_id);
