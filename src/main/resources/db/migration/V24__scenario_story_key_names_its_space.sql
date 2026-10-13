update simulator_scenario_story_state
set story_key = 'test-run:' || story_key
where keyed_on = 'TEST_RUN';

delete from simulator_scenario_story_state
where story_key !~ '^[a-z][a-z0-9-]*:'
   or btrim(
          substring(story_key from position(':' in story_key) + 1),
          E'\u0009\u000A\u000B\u000C\u000D\u001C\u001D\u001E\u001F\u0020'
              || E'\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006'
              || E'\u2008\u2009\u200A\u2028\u2029\u205F\u3000'
      ) = '';

alter table simulator_scenario_story_state
    add constraint simulator_scenario_story_state_names_its_space check (
        story_key ~ '^[a-z][a-z0-9-]*:'
        and btrim(
            substring(story_key from position(':' in story_key) + 1),
            E'\u0009\u000A\u000B\u000C\u000D\u001C\u001D\u001E\u001F\u0020'
                || E'\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006'
                || E'\u2008\u2009\u200A\u2028\u2029\u205F\u3000'
        ) <> ''
    );
