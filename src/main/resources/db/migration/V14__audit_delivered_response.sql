alter table simulator_audit_record
    add column delivered_fault            varchar(32),
    add column delivered_declared_length  bigint,
    add column delivered_body_bytes       bigint,
    add column delivered_held_for_ms      bigint;

alter table simulator_audit_record
    add constraint simulator_audit_record_delivery check (
        (delivered_declared_length is null) = (delivered_body_bytes is null)
        and (delivered_declared_length is null) = (delivered_held_for_ms is null)
        and (delivered_fault is null or delivered_declared_length is not null)
        and (delivered_declared_length is null or delivered_declared_length >= 0)
        and (delivered_body_bytes is null or delivered_body_bytes >= 0)
        and (delivered_held_for_ms is null or delivered_held_for_ms >= 0)
    );
