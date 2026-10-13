alter table simulator_webhook_delivery_attempt
    alter column sent_headers drop not null;

update simulator_webhook_delivery_attempt
   set sent_headers = null;
