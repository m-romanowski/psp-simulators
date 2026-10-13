create table azericard_stored_card (
    test_run_id       varchar(64)  not null,
    token             varchar(28)  not null,
    terminal_id       varchar(32)  not null,
    merchant_id       varchar(128) not null,
    stored_by_int_ref varchar(32)  not null,
    order_id          varchar(32)  not null,
    created_at        timestamptz  not null,
    primary key (test_run_id, token)
);

create index azericard_stored_card_by_terminal
    on azericard_stored_card (test_run_id, terminal_id);
