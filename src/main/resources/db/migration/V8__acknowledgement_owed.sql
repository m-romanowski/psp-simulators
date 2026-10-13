alter table simulator_webhook_delivery
    add column acknowledgement_owed boolean not null default false;

alter table simulator_webhook_delivery
    alter column acknowledgement_owed drop default;

create index grow_payment_process_by_transaction
    on grow_payment_process (test_run_id, merchant_id, ((outcome::jsonb) ->> 'transactionId'));
