alter table simulator_webhook_delivery
    alter column target_url drop not null;

alter table simulator_webhook_delivery
    add constraint simulator_webhook_delivery_with_no_target_is_undeliverable
        check (target_url is not null or state = 'UNDELIVERABLE');

alter table simulator_audit_record
    alter column path drop not null;

alter table simulator_audit_record
    add constraint simulator_audit_record_with_no_path_is_a_callback_not_sent
        check (path is not null or direction = 'OUTBOUND_WEBHOOK_NOT_SENT');
