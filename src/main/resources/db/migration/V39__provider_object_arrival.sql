create sequence simulator_provider_object_arrival;

alter table grow_transaction
    add column arrival bigint not null default nextval('simulator_provider_object_arrival');

alter table azericard_payment
    add column arrival bigint not null default nextval('simulator_provider_object_arrival');

alter table azericard_stored_card
    add column arrival bigint not null default nextval('simulator_provider_object_arrival');

alter table stripe_payment_intent
    add column arrival bigint not null default nextval('simulator_provider_object_arrival');

alter table stripe_refund
    add column arrival bigint not null default nextval('simulator_provider_object_arrival');

create unique index grow_transaction_by_arrival
    on grow_transaction (test_run_id, arrival);

create unique index azericard_payment_by_arrival
    on azericard_payment (test_run_id, arrival);

create unique index azericard_stored_card_by_arrival
    on azericard_stored_card (test_run_id, arrival);

create unique index stripe_payment_intent_by_arrival
    on stripe_payment_intent (test_run_id, arrival);

create unique index stripe_refund_by_arrival
    on stripe_refund (test_run_id, arrival);
