delete from stripe_customer where customer_id like 'ch/_sim/_%' escape '/';

delete from stripe_payment_method where payment_method_id like 'ch/_sim/_%' escape '/';

delete from stripe_setup_intent where setup_intent_id like 'ch/_sim/_%' escape '/';

alter table stripe_customer
    add constraint stripe_customer_is_not_a_charge
        check (customer_id not like 'ch/_sim/_%' escape '/');

alter table stripe_payment_method
    add constraint stripe_payment_method_is_not_a_charge
        check (payment_method_id not like 'ch/_sim/_%' escape '/');

alter table stripe_setup_intent
    add constraint stripe_setup_intent_is_not_a_charge
        check (setup_intent_id not like 'ch/_sim/_%' escape '/');

create table stripe_charge (
    test_run_id    varchar(64)  not null,
    charge_id      varchar(128) not null,
    intent_id      varchar(64)  not null,
    account_id     varchar(128) not null,
    amount         text         not null,
    currency       text         not null,
    payment_method varchar(128),
    status         varchar(32)  not null,
    created_at     timestamptz  not null,
    arrival        bigint       not null default nextval('simulator_provider_object_arrival'),
    primary key (test_run_id, charge_id),
    constraint stripe_charge_amount_is_minor_units
        check (amount ~ '^[1-9][0-9]{0,17}$'),
    constraint stripe_charge_status
        check (status in ('succeeded', 'failed')),
    constraint stripe_charge_is_not_the_payment_it_charges
        check (charge_id <> intent_id),
    constraint stripe_charge_is_not_a_payment_intent
        check (charge_id not like 'pi/_sim/_%' escape '/'),
    constraint stripe_charge_is_not_a_refund
        check (charge_id not like 're/_sim/_%' escape '/'),
    constraint stripe_charge_is_not_a_customer
        check (charge_id not like 'cus/_sim/_%' escape '/'),
    constraint stripe_charge_is_not_a_setup_intent
        check (charge_id not like 'seti/_sim/_%' escape '/'),
    constraint stripe_charge_is_addressable_within_its_payment
        unique (test_run_id, intent_id, charge_id),
    foreign key (test_run_id, intent_id)
        references stripe_payment_intent (test_run_id, intent_id) on delete cascade
);

create index stripe_charge_by_intent on stripe_charge (test_run_id, intent_id, arrival desc);

create table stripe_charge_id_claim (
    test_run_id varchar(64)  not null,
    charge_id   varchar(128) not null,
    primary key (test_run_id, charge_id)
);

alter table stripe_refund
    add column charge_id varchar(128);

alter table stripe_refund
    add constraint stripe_refund_charge_is_a_charge_of_the_payment_it_returns
        foreign key (test_run_id, intent_id, charge_id)
            references stripe_charge (test_run_id, intent_id, charge_id) on delete cascade;
