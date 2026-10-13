create table stripe_account (
    test_run_id    varchar(64)  not null,
    account_id     varchar(128) not null,
    secret_key     varchar(255) not null,
    capture_window text         not null,
    primary key (test_run_id, account_id),
    constraint stripe_account_secret_key_is_a_test_mode_key
        check (secret_key like 'sk\_test\_%'),
    constraint stripe_account_capture_window_is_a_duration
        check (capture_window ~ '^PT[0-9]')
);

create unique index stripe_account_by_secret_key on stripe_account (test_run_id, secret_key);

create index stripe_account_by_credentials on stripe_account (secret_key);
