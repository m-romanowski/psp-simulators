create table simulator_route_alias (
    alias_key   varchar(512) primary key,
    provider    varchar(32)  not null,
    test_run_id varchar(64)  not null,
    from_url    text         not null,
    to_route    text         not null,
    updated_at  timestamptz  not null
);

create index simulator_route_alias_by_run
    on simulator_route_alias (provider, test_run_id);
