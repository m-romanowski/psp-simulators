alter table simulator_audit_record
    add column origin varchar(32);

update simulator_audit_record
set origin = case
                 when direction in ('OUTBOUND_WEBHOOK_ATTEMPT',
                                    'OUTBOUND_WEBHOOK_DROPPED',
                                    'OUTBOUND_WEBHOOK_NOT_SENT') then 'SCHEDULED_JOB'
                 else 'PROVIDER_API'
                 end
where origin is null;

alter table simulator_audit_record
    alter column origin set not null;

create index simulator_audit_record_by_origin
    on simulator_audit_record (provider, test_run_id, origin, seq);
