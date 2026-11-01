alter table stripe_account
    add column publishable_key varchar(255);

alter table stripe_account
    add constraint stripe_account_publishable_key_is_a_test_mode_key
        check (publishable_key is null or publishable_key like 'pk\_test\_%');

create unique index stripe_account_by_publishable_key on stripe_account (test_run_id, publishable_key);

create index stripe_account_by_publishable_credentials on stripe_account (publishable_key);
