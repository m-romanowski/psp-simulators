alter table simulator_test_run
    alter column fixed_now drop not null;

delete from simulator_provider_account where test_run_id = 'UNKNOWN';
delete from simulator_webhook_target where test_run_id = 'UNKNOWN';
delete from grow_payment_process where test_run_id = 'UNKNOWN';
delete from simulator_test_run where test_run_id = 'UNKNOWN';

insert into simulator_test_run (test_run_id)
select test_run_id
from (select test_run_id from simulator_provider_account
      union
      select test_run_id from simulator_webhook_target
      union
      select test_run_id from grow_payment_process) configured
on conflict (test_run_id) do nothing;
