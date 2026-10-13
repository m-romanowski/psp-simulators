create table simulator_test_run (
    test_run_id varchar(64) primary key,
    fixed_now   timestamptz not null
);

create table simulator_provider_account (
    provider    varchar(32)  not null,
    test_run_id varchar(64)  not null,
    merchant_id varchar(128) not null,
    user_id     text,
    page_code   text,
    api_key     text,
    api_secret  text,
    terminal_id text,
    updated_at  timestamptz  not null,
    primary key (provider, test_run_id, merchant_id)
);

create index simulator_provider_account_by_provider
    on simulator_provider_account (provider);

create table simulator_webhook_target (
    provider    varchar(32)  not null,
    test_run_id varchar(64)  not null,
    merchant_id varchar(128) not null,
    url         text         not null,
    secret      text,
    key_id      text,
    updated_at  timestamptz  not null,
    primary key (provider, test_run_id, merchant_id, url)
);

create table simulator_audit_record (
    audit_id           varchar(64) primary key,
    seq                bigserial   not null,
    provider           varchar(32) not null,
    test_run_id        varchar(64) not null,
    resolution_rule    varchar(32) not null,
    direction          varchar(32) not null,
    outcome            varchar(16),
    method             varchar(16) not null,
    path               text        not null,
    http_status        integer,
    provider_object_id text,
    scenario_id        text,
    attempt            integer     not null,
    created_at         timestamptz not null,
    sanitized_headers  text        not null,
    sanitized_body     text        not null
);

create index simulator_audit_record_by_run
    on simulator_audit_record (provider, test_run_id, seq);

create table grow_payment_process (
    test_run_id        varchar(64)  not null,
    process_id         varchar(32)  not null,
    process_token      varchar(64)  not null,
    merchant_id        varchar(128) not null,
    charge_type        varchar(8)   not null,
    charge_sum         text         not null,
    description        text         not null,
    save_card_token    boolean      not null,
    success_url        text         not null,
    cancel_url         text         not null,
    notify_url         text,
    payer_full_name    text         not null,
    payer_phone        text         not null,
    payer_email        text,
    custom_fields      text         not null,
    hosted_page_url    text         not null,
    native_status_code varchar(8)   not null,
    native_status      text         not null,
    normalized_status  varchar(32)  not null,
    origin             varchar(16)  not null,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,
    primary key (test_run_id, process_id)
);
