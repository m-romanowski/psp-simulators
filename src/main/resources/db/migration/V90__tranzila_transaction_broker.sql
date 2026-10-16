alter table tranzila_transaction
    add column broker varchar(16);

alter table tranzila_transaction
    add constraint tranzila_transaction_broker
        check (broker is null or broker in ('apple_pay', 'GOOGLE_PAY'));
