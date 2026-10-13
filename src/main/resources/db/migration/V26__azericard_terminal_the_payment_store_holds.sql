delete from simulator_provider_account
where provider = 'azericard'
  and length(terminal_id) > 32;
