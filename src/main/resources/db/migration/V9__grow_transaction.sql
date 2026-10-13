create table grow_transaction (
    test_run_id       varchar(64)  not null,
    transaction_id    varchar(32)  not null,
    transaction_token varchar(64)  not null,
    merchant_id       varchar(128) not null,
    process_id        varchar(32)  not null,
    asmachta          text         not null,
    payment_date      varchar(16)  not null,
    description       text         not null,
    payer_full_name   text         not null,
    payer_phone       text         not null,
    payer_email       text,
    amount            text         not null,
    origin            varchar(16)  not null,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    primary key (test_run_id, transaction_id)
);

create table grow_transaction_operation (
    test_run_id          varchar(64) not null,
    transaction_id       varchar(32) not null,
    seq                  integer     not null,
    kind                 varchar(32) not null,
    amount               text        not null,
    closes_authorization boolean,
    native_status        varchar(8)  not null,
    normalized_status    varchar(32) not null,
    created_at           timestamptz not null,
    primary key (test_run_id, transaction_id, seq),
    foreign key (test_run_id, transaction_id)
        references grow_transaction (test_run_id, transaction_id) on delete cascade
);
