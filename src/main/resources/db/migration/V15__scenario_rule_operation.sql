alter table simulator_scenario_rule
    add column match_operation text;

alter table simulator_scenario_rule
    drop constraint simulator_scenario_rule_action_shape;

alter table simulator_scenario_rule
    add constraint simulator_scenario_rule_action_shape check (
        case action_type
            when 'FIXED_SUCCESS' then
                action_provider_code is null
                and action_fault is null
                and failures_before_success is null
            when 'TERMINAL_FAILURE' then
                action_provider_code is not null
                and action_fault is null
                and failures_before_success is null
            when 'TRANSIENT_THEN_SUCCESS' then
                action_provider_code is not null
                and action_fault is null
                and failures_before_success is not null
            when 'TRANSPORT_FAULT' then
                action_provider_code is null
                and action_fault is not null
                and failures_before_success is null
            when 'TRANSPORT_FAULT_THEN_SUCCESS' then
                action_provider_code is null
                and action_fault is not null
                and failures_before_success is not null
            else false
        end
        and (action_fault is null or action_fault in (
            'SERVER_ERROR', 'TRUNCATED_BODY', 'CORRUPTED_BODY',
            'DROPPED_CONNECTION', 'DELAYED_RESPONSE'))
        and (coalesce(action_fault, '') = 'SERVER_ERROR')
            = (action_http_status is not null)
        and (coalesce(action_fault, '') = 'DELAYED_RESPONSE')
            = (action_delay_ms is not null)
        and (failures_before_success is null or failures_before_success >= 1)
        and (action_http_status is null
            or (action_http_status between 500 and 599))
        and (action_delay_ms is null or action_delay_ms >= 1)
    );
