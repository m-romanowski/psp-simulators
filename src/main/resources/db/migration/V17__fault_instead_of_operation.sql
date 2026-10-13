alter table simulator_scenario_rule
    alter column action_type type varchar(64);

alter table simulator_scenario_rule
    drop constraint simulator_scenario_rule_action_shape;

alter table simulator_scenario_rule
    add constraint simulator_scenario_rule_action_shape check (
        case action_type
            when 'FIXED_SUCCESS' then
                action_provider_code is null
                and action_fault is null
            when 'TERMINAL_FAILURE' then
                action_provider_code is not null
                and action_fault is null
            when 'TRANSPORT_FAULT' then
                action_provider_code is null
                and action_fault is not null
            when 'TRANSPORT_FAULT_INSTEAD_OF_OPERATION' then
                action_provider_code is null
                and action_fault = 'SERVER_ERROR'
            else false
        end
        and (action_fault is null or action_fault in (
            'SERVER_ERROR', 'TRUNCATED_BODY', 'CORRUPTED_BODY',
            'DROPPED_CONNECTION', 'DELAYED_RESPONSE'))
        and (coalesce(action_fault, '') = 'SERVER_ERROR')
            = (action_http_status is not null)
        and (coalesce(action_fault, '') = 'DELAYED_RESPONSE')
            = (action_delay_ms is not null)
        and (action_http_status is null
            or (action_http_status between 500 and 599))
        and (action_delay_ms is null or action_delay_ms >= 1)
    );

alter table simulator_audit_record
    add column delivered_fault_placement varchar(32);

update simulator_audit_record
set delivered_fault_placement = 'AFTER_THE_OPERATION'
where delivered_fault is not null;

alter table simulator_audit_record
    add constraint simulator_audit_record_delivery_placement check (
        (delivered_fault is null) = (delivered_fault_placement is null)
        and (delivered_fault is null or delivered_fault in (
            'SERVER_ERROR', 'TRUNCATED_BODY', 'CORRUPTED_BODY',
            'DROPPED_CONNECTION', 'DELAYED_RESPONSE'))
        and (delivered_fault_placement is null or delivered_fault_placement in (
            'AFTER_THE_OPERATION', 'INSTEAD_OF_THE_OPERATION'))
        and (delivered_fault_placement is distinct from 'INSTEAD_OF_THE_OPERATION'
            or (delivered_fault = 'SERVER_ERROR' and outcome = 'UNKNOWN'))
    );
