create table simulator_scenario_rule (
    scenario_id               varchar(64) primary key,
    seq                       bigserial   not null unique,
    provider                  varchar(32) not null,
    test_run_id               varchar(64) not null,
    priority                  integer     not null,
    match_merchant_id         text,
    match_order_id            text,
    match_provider_object_id  text,
    match_customer_id         text,
    match_payment_method_id   text,
    match_amount              text,
    match_currency            text,
    match_header              text        not null,
    action_type               varchar(32) not null,
    action_provider_code      text        not null,
    failures_before_success   integer,
    failures_consumed         integer     not null,
    created_at                timestamptz not null
);

create index simulator_scenario_rule_by_run
    on simulator_scenario_rule (provider, test_run_id, seq);
