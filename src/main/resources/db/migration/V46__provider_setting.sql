create table simulator_provider_setting (
    provider      varchar(32) not null,
    test_run_id   varchar(64) not null,
    setting_key   varchar(64) not null,
    setting_value text        not null,
    updated_at    timestamptz not null,
    primary key (provider, test_run_id, setting_key)
);
