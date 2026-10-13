delete from simulator_scenario_rule
where action_type in ('TRANSIENT_THEN_SUCCESS', 'TRANSPORT_FAULT_THEN_SUCCESS');

alter table simulator_scenario_rule
    drop constraint simulator_scenario_rule_action_shape;

alter table simulator_scenario_rule
    drop column failures_before_success,
    drop column failures_consumed;

create domain simulator_scenario_label as text check (
    btrim(
        value,
        E'\u0009\u000A\u000B\u000C\u000D\u001C\u001D\u001E\u001F\u0020'
            || E'\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006'
            || E'\u2008\u2009\u200A\u2028\u2029\u205F\u3000'
    ) <> ''
    and length(value) <= 64
);

alter table simulator_scenario_rule
    add column story simulator_scenario_label,
    add column when_state simulator_scenario_label,
    add column then_state simulator_scenario_label;

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

alter table simulator_scenario_rule
    add constraint simulator_scenario_rule_story_shape check (
        (story is not null or (when_state is null and then_state is null))
        and (then_state is null or when_state is null or then_state <> when_state)
    );

create table simulator_scenario_story_state (
    provider    varchar(32)              not null,
    test_run_id varchar(64)              not null,
    story       simulator_scenario_label not null,
    object_id   text,
    state       simulator_scenario_label not null,
    updated_at  timestamptz              not null,
    constraint simulator_scenario_story_state_one_per_object
        unique nulls not distinct (provider, test_run_id, story, object_id),
    constraint simulator_scenario_story_state_shape check (
        object_id is null or btrim(object_id) <> ''
    )
);
