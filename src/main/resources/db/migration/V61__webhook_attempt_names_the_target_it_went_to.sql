alter table simulator_webhook_delivery_attempt
    add column target_url text;

update simulator_webhook_delivery_attempt attempt
   set target_url = delivery.target_url
  from simulator_webhook_delivery delivery
 where delivery.delivery_id = attempt.delivery_id
   and attempt.target_url is null;

alter table simulator_webhook_delivery_attempt
    alter column target_url set not null;
