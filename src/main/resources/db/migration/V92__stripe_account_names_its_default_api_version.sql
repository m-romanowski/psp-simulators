alter table stripe_account
    add column api_version varchar(32) not null default '2026-10-28.endive';

alter table stripe_account
    alter column api_version drop default;

alter table stripe_account
    add constraint stripe_account_api_version_is_a_release_of_a_major
        check (api_version ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}\.[a-z]+$' and api_version !~ '\.preview$');
