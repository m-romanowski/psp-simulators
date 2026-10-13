alter table grow_transaction
    add column kind varchar(16) not null default 'PAYMENT',
    add column refunded_transaction_id varchar(32),
    alter column transaction_token drop not null,
    alter column process_id drop not null;

alter table grow_transaction
    alter column kind drop default;

alter table grow_transaction
    add constraint grow_transaction_identity_shape check (
        (kind = 'PAYMENT'
            and transaction_token is not null
            and process_id is not null
            and refunded_transaction_id is null)
        or (kind = 'REFUND'
            and transaction_token is null
            and process_id is null
            and refunded_transaction_id is not null));
