alter table simulator_webhook_delivery_attempt
    add column sent_headers text;

update simulator_webhook_delivery_attempt attempt
   set sent_headers = delivery.headers
  from simulator_webhook_delivery delivery
 where delivery.delivery_id = attempt.delivery_id
   and attempt.sent_headers is null;

alter table simulator_webhook_delivery_attempt
    alter column sent_headers set not null;
