delete from simulator_scenario_story_state
where object_id is null;

alter table simulator_scenario_story_state
    drop constraint simulator_scenario_story_state_one_per_object;

alter table simulator_scenario_story_state
    drop constraint simulator_scenario_story_state_shape;

alter table simulator_scenario_story_state
    rename column object_id to story_key;

alter table simulator_scenario_story_state
    alter column story_key set not null;

alter table simulator_scenario_story_state
    add constraint simulator_scenario_story_state_one_per_flow
        unique (provider, test_run_id, story, story_key);

alter table simulator_scenario_story_state
    add constraint simulator_scenario_story_state_shape check (
        btrim(
            story_key,
            E'\u0009\u000A\u000B\u000C\u000D\u001C\u001D\u001E\u001F\u0020'
                || E'\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006'
                || E'\u2008\u2009\u200A\u2028\u2029\u205F\u3000'
        ) <> ''
    );
