create table azericard_payment (
    test_run_id   varchar(64)  not null,
    int_ref       varchar(32)  not null,
    terminal_id   varchar(32)  not null,
    merchant_id   varchar(128) not null,
    order_id      varchar(32)  not null,
    trtype        varchar(2)   not null,
    amount        text         not null,
    currency      text         not null,
    approval      varchar(16),
    rrn           varchar(16)  not null,
    action_code   varchar(2)   not null,
    response_code varchar(8)   not null,
    origin        varchar(16)  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    primary key (test_run_id, int_ref)
);

create index azericard_payment_by_order
    on azericard_payment (test_run_id, terminal_id, order_id);

create table azericard_payment_operation (
    test_run_id          varchar(64) not null,
    int_ref              varchar(32) not null,
    seq                  integer     not null,
    kind                 varchar(32) not null,
    amount               text        not null,
    closes_authorization boolean,
    provider_object_id   varchar(64),
    native_status        varchar(16) not null,
    normalized_status    varchar(32) not null,
    created_at           timestamptz not null,
    primary key (test_run_id, int_ref, seq),
    foreign key (test_run_id, int_ref)
        references azericard_payment (test_run_id, int_ref) on delete cascade
);

create table azericard_int_ref_claim (
    test_run_id varchar(64) not null,
    int_ref     varchar(32) not null,
    primary key (test_run_id, int_ref)
);

create table azericard_order_trace (
    test_run_id varchar(64) not null,
    terminal_id varchar(32) not null,
    trace_day   date        not null,
    trace       varchar(6)  not null,
    primary key (test_run_id, terminal_id, trace_day, trace)
);
