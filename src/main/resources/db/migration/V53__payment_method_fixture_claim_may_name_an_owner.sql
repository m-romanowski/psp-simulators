alter table simulator_payment_method_fixture_claim
    add column claims_the_value_alone boolean not null default true;

alter table simulator_payment_method_fixture_claim
    drop constraint simulator_payment_method_fixture_claim_pkey;

alter table simulator_payment_method_fixture_claim
    add constraint simulator_payment_method_fixture_claim_pkey
        primary key (provider, test_run_id, id_key, id_value, fixture_id);

create unique index simulator_payment_method_fixture_claim_alone
    on simulator_payment_method_fixture_claim (provider, test_run_id, id_key, id_value)
    where claims_the_value_alone;
