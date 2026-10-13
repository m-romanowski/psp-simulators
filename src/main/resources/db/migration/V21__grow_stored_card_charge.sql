create table grow_transaction_id_claim (
    test_run_id    varchar(64) not null,
    transaction_id varchar(32) not null,
    primary key (test_run_id, transaction_id)
);

insert into grow_transaction_id_claim (test_run_id, transaction_id)
select test_run_id, transaction_id from grow_transaction;

create table grow_stored_card_charge (
    test_run_id       varchar(64)  not null,
    merchant_id       varchar(128) not null,
    unique_identifier text         not null,
    group_identifier  text,
    transaction_id    varchar(32),
    custom_fields     text         not null,
    created_at        timestamptz  not null,
    primary key (test_run_id, merchant_id, unique_identifier)
);

create unique index grow_stored_card_charge_one_group_per_charged_payment
    on grow_stored_card_charge (test_run_id, merchant_id, group_identifier)
    where transaction_id is not null and group_identifier is not null;

create unique index grow_stored_card_charge_one_submission_per_transaction
    on grow_stored_card_charge (test_run_id, transaction_id)
    where transaction_id is not null;

alter table grow_transaction
    drop constraint grow_transaction_identity_shape;

alter table grow_transaction
    add constraint grow_transaction_identity_shape check (
        (kind = 'PAYMENT'
            and transaction_token is not null
            and process_id is not null
            and refunded_transaction_id is null)
        or (kind = 'TOKEN_PAYMENT'
            and transaction_token is not null
            and process_id is null
            and refunded_transaction_id is null)
        or (kind = 'REFUND'
            and transaction_token is null
            and process_id is null
            and refunded_transaction_id is not null));
