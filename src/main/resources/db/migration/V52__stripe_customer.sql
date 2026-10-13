create table stripe_customer (
    test_run_id varchar(64)  not null,
    customer_id varchar(128) not null,
    account_id  varchar(128) not null,
    email       text,
    deleted_at  timestamptz,
    origin      varchar(16)  not null,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    arrival     bigint       not null default nextval('simulator_provider_object_arrival'),
    primary key (test_run_id, customer_id),
    constraint stripe_customer_is_not_a_payment_intent
        check (customer_id not like 'pi/_sim/_%' escape '/'),
    constraint stripe_customer_is_not_a_refund
        check (customer_id not like 're/_sim/_%' escape '/')
);

create unique index stripe_customer_by_arrival on stripe_customer (test_run_id, arrival);

create index stripe_customer_by_account on stripe_customer (test_run_id, account_id);
