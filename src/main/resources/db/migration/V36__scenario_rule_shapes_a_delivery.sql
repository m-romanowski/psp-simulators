alter table simulator_scenario_rule
    add column match_event_type text;

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
            when 'DELIVERY_DELAYED' then
                action_provider_code is null
                and action_fault is null
            when 'DELIVERY_DUPLICATED' then
                action_provider_code is null
                and action_fault is null
            when 'DELIVERY_DROPPED' then
                action_provider_code is null
                and action_fault is null
            else false
        end
        and (action_fault is null or action_fault in (
            'SERVER_ERROR', 'TRUNCATED_BODY', 'CORRUPTED_BODY',
            'DROPPED_CONNECTION', 'DELAYED_RESPONSE'))
        and (coalesce(action_fault, '') = 'SERVER_ERROR')
            = (action_http_status is not null)
        and (coalesce(action_fault, '') = 'DELAYED_RESPONSE'
            or action_type = 'DELIVERY_DELAYED')
            = (action_delay_ms is not null)
        and (action_http_status is null
            or (action_http_status between 500 and 599))
        and (action_delay_ms is null or action_delay_ms >= 1)
        and (action_type <> 'DELIVERY_DELAYED' or action_delay_ms <= 3600000)
    );

alter table simulator_scenario_rule
    add constraint simulator_scenario_rule_subject check (
        action_type not in ('DELIVERY_DELAYED', 'DELIVERY_DUPLICATED', 'DELIVERY_DROPPED')
        or (
            match_order_id is null
            and match_customer_id is null
            and match_payment_method_id is null
            and match_amount is null
            and match_currency is null
            and match_operation is null
            and match_header = '{}'
            and story is null
        )
    );

alter table simulator_scenario_rule
    add constraint simulator_scenario_rule_event_type check (
        match_event_type is null
        or action_type in ('DELIVERY_DELAYED', 'DELIVERY_DUPLICATED', 'DELIVERY_DROPPED')
    );
