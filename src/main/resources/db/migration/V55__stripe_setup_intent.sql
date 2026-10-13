delete from stripe_customer where customer_id like 'seti/_sim/_%' escape '/';

delete from stripe_payment_method where payment_method_id like 'seti/_sim/_%' escape '/';

alter table stripe_customer
    add constraint stripe_customer_is_not_a_setup_intent
        check (customer_id not like 'seti/_sim/_%' escape '/');

alter table stripe_payment_method
    add constraint stripe_payment_method_is_not_a_setup_intent
        check (payment_method_id not like 'seti/_sim/_%' escape '/');

create table stripe_setup_intent (
    test_run_id       varchar(64)  not null,
    setup_intent_id   varchar(128) not null,
    account_id        varchar(128) not null,
    customer_id       varchar(128),
    payment_method_id varchar(128),
    intended_usage    varchar(16)  not null,
    status            varchar(32)  not null,
    client_secret     varchar(256) not null,
    last_setup_error  varchar(64),
    origin            varchar(16)  not null,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    arrival           bigint       not null default nextval('simulator_provider_object_arrival'),
    primary key (test_run_id, setup_intent_id),
    constraint stripe_setup_intent_usage
        check (intended_usage in ('ON_SESSION', 'OFF_SESSION')),
    constraint stripe_setup_intent_status check (
        status in ('REQUIRES_PAYMENT_METHOD', 'REQUIRES_CONFIRMATION', 'REQUIRES_ACTION',
                   'PROCESSING', 'CANCELED', 'SUCCEEDED')),
    constraint stripe_setup_intent_failure_returns_to_requires_payment_method
        check (last_setup_error is null or status = 'REQUIRES_PAYMENT_METHOD'),
    constraint stripe_setup_intent_a_succeeded_setup_set_a_method_up
        check (status <> 'SUCCEEDED' or payment_method_id is not null),
    constraint stripe_setup_intent_is_not_a_payment_intent
        check (setup_intent_id not like 'pi/_sim/_%' escape '/'),
    constraint stripe_setup_intent_is_not_a_refund
        check (setup_intent_id not like 're/_sim/_%' escape '/'),
    constraint stripe_setup_intent_is_not_a_customer
        check (setup_intent_id not like 'cus/_sim/_%' escape '/')
);

create unique index stripe_setup_intent_by_arrival
    on stripe_setup_intent (test_run_id, arrival);

create index stripe_setup_intent_by_customer
    on stripe_setup_intent (test_run_id, account_id, customer_id);
