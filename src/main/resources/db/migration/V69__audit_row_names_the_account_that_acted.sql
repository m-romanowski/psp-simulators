alter table simulator_audit_record
    add column account_id varchar(128);

alter table simulator_audit_record
    add constraint simulator_audit_record_account_named check (
        account_id is null or account_id <> ''
        );

create index simulator_audit_record_by_account
    on simulator_audit_record (provider, test_run_id, account_id, seq);
