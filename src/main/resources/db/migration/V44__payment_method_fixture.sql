create table simulator_payment_method_fixture (
    fixture_id       varchar(64)  primary key,
    seq              bigserial    not null unique,
    provider         varchar(32)  not null,
    test_run_id      varchar(64)  not null,
    merchant_id      varchar(128) not null,
    owner_id         varchar(128) not null,
    method_type      varchar(16)  not null,
    fixture_alias    varchar(128) not null,
    card_brand       varchar(32),
    card_last4       varchar(4),
    card_exp_month   integer,
    card_exp_year    integer,
    card_funding     varchar(32),
    provider_ids     text         not null,
    default_scenario varchar(64),
    created_at       timestamptz  not null,
    constraint simulator_payment_method_fixture_type
        check (method_type in ('CARD', 'WALLET', 'PSP_TOKEN')),
    constraint simulator_payment_method_fixture_card_shape check (
        (method_type = 'CARD'
            and card_brand is not null
            and card_last4 is not null
            and card_exp_month is not null
            and card_exp_year is not null
            and card_funding is not null)
        or (method_type <> 'CARD'
            and card_brand is null
            and card_last4 is null
            and card_exp_month is null
            and card_exp_year is null
            and card_funding is null)),
    constraint simulator_payment_method_fixture_last4
        check (card_last4 is null or card_last4 ~ '^[0-9]{4}$'),
    constraint simulator_payment_method_fixture_exp_month
        check (card_exp_month is null or card_exp_month between 1 and 12),
    constraint simulator_payment_method_fixture_exp_year
        check (card_exp_year is null or card_exp_year between 1000 and 9999)
);

create unique index simulator_payment_method_fixture_by_alias
    on simulator_payment_method_fixture (provider, test_run_id, merchant_id, fixture_alias);

create index simulator_payment_method_fixture_by_run
    on simulator_payment_method_fixture (provider, test_run_id, seq);

create table simulator_payment_method_fixture_claim (
    provider    varchar(32) not null,
    test_run_id varchar(64) not null,
    id_key      varchar(64)  not null,
    id_value    varchar(256) not null,
    fixture_id  varchar(64) not null
        references simulator_payment_method_fixture (fixture_id) on delete cascade,
    primary key (provider, test_run_id, id_key, id_value)
);

create index simulator_payment_method_fixture_claim_by_fixture
    on simulator_payment_method_fixture_claim (fixture_id);
