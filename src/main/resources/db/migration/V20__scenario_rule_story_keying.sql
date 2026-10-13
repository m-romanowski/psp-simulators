alter table simulator_scenario_rule
    add column keyed_on varchar(16);

update simulator_scenario_rule
set keyed_on = 'FLOW'
where story is not null;

alter table simulator_scenario_rule
    drop constraint simulator_scenario_rule_story_shape;

alter table simulator_scenario_rule
    add constraint simulator_scenario_rule_story_shape check (
        (story is not null or (when_state is null and then_state is null))
        and (then_state is null or when_state is null or then_state <> when_state)
        and (story is null) = (keyed_on is null)
        and (keyed_on is null or keyed_on in ('FLOW', 'TEST_RUN'))
    );

alter table simulator_scenario_story_state
    add column keyed_on varchar(16) not null default 'FLOW';

alter table simulator_scenario_story_state
    alter column keyed_on drop default;

alter table simulator_scenario_story_state
    drop constraint simulator_scenario_story_state_one_per_flow;

alter table simulator_scenario_story_state
    add constraint simulator_scenario_story_state_one_per_key
        unique (provider, test_run_id, story, keyed_on, story_key);

alter table simulator_scenario_story_state
    add constraint simulator_scenario_story_state_keyed_on check (
        keyed_on in ('FLOW', 'TEST_RUN')
    );
