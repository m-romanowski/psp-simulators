create table azericard_payer_session (
    session_id  varchar(64)  not null,
    test_run_id varchar(64)  not null,
    route       varchar(16)  not null,
    terminal_id varchar(32)  not null,
    merchant_id varchar(128) not null,
    form        text         not null,
    headers     text         not null,
    outcome     varchar(16),
    int_ref     varchar(32),
    origin      varchar(16)  not null,
    created_at  timestamptz  not null,
    settled_at  timestamptz,
    arrival     bigint       not null default nextval('simulator_provider_object_arrival'),
    primary key (session_id)
);

create unique index azericard_payer_session_by_arrival
    on azericard_payer_session (test_run_id, arrival);

alter table azericard_order_trace
    add column held_by_session varchar(64)
        references azericard_payer_session (session_id) on delete cascade;

create unique index azericard_order_trace_held_by_session
    on azericard_order_trace (test_run_id, held_by_session)
    where held_by_session is not null;

alter table azericard_payment
    add column card_last4 varchar(4);

alter table azericard_payment
    add column ext_net_ref varchar(32);

alter table azericard_stored_card
    add column ext_net_ref varchar(32);
