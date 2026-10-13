delete from stripe_customer where customer_id like 'ek/_sim/_%' escape '/';

delete from stripe_payment_method where payment_method_id like 'ek/_sim/_%' escape '/';

delete from stripe_setup_intent where setup_intent_id like 'ek/_sim/_%' escape '/';

delete from stripe_charge where charge_id like 'ek/_sim/_%' escape '/';

alter table stripe_customer
    add constraint stripe_customer_is_not_an_ephemeral_key
        check (customer_id not like 'ek/_sim/_%' escape '/');

alter table stripe_payment_method
    add constraint stripe_payment_method_is_not_an_ephemeral_key
        check (payment_method_id not like 'ek/_sim/_%' escape '/');

alter table stripe_setup_intent
    add constraint stripe_setup_intent_is_not_an_ephemeral_key
        check (setup_intent_id not like 'ek/_sim/_%' escape '/');

alter table stripe_charge
    add constraint stripe_charge_is_not_an_ephemeral_key
        check (charge_id not like 'ek/_sim/_%' escape '/');

create table stripe_ephemeral_key (
    test_run_id      varchar(64)  not null,
    ephemeral_key_id varchar(128) not null,
    account_id       varchar(128) not null,
    customer_id      varchar(128) not null,
    secret           varchar(256) not null,
    created_at       timestamptz  not null,
    expires_at       timestamptz  not null,
    primary key (test_run_id, ephemeral_key_id),
    constraint stripe_ephemeral_key_outlives_the_moment_it_was_minted
        check (expires_at > created_at),
    constraint stripe_ephemeral_key_is_not_the_resource_it_reaches
        check (ephemeral_key_id <> customer_id),
    constraint stripe_ephemeral_key_is_not_a_payment_intent
        check (ephemeral_key_id not like 'pi/_sim/_%' escape '/'),
    constraint stripe_ephemeral_key_is_not_a_refund
        check (ephemeral_key_id not like 're/_sim/_%' escape '/'),
    constraint stripe_ephemeral_key_is_not_a_customer
        check (ephemeral_key_id not like 'cus/_sim/_%' escape '/'),
    constraint stripe_ephemeral_key_is_not_a_setup_intent
        check (ephemeral_key_id not like 'seti/_sim/_%' escape '/'),
    constraint stripe_ephemeral_key_is_not_a_charge
        check (ephemeral_key_id not like 'ch/_sim/_%' escape '/'),
    foreign key (test_run_id, customer_id)
        references stripe_customer (test_run_id, customer_id) on delete cascade
);

create unique index stripe_ephemeral_key_by_secret on stripe_ephemeral_key (secret);

create index stripe_ephemeral_key_by_customer
    on stripe_ephemeral_key (test_run_id, account_id, customer_id);
