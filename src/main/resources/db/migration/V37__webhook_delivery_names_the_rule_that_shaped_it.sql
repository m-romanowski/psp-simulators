alter table simulator_webhook_delivery
    add column shaped_by text;

create index simulator_webhook_delivery_by_rule
    on simulator_webhook_delivery (provider, test_run_id, shaped_by);
