alter table simulator_webhook_delivery
    add column headers text not null default '{}';

alter table simulator_webhook_delivery
    alter column headers drop default;

alter table simulator_webhook_delivery_attempt
    drop constraint simulator_webhook_delivery_attempt_delivery_id_fkey;

alter table simulator_webhook_delivery_attempt
    add constraint simulator_webhook_delivery_attempt_delivery_id_fkey
        foreign key (delivery_id) references simulator_webhook_delivery (delivery_id)
            on delete cascade;
