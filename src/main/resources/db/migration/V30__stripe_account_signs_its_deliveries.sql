alter table stripe_account
    add column webhook_secret text;

alter table stripe_account
    add column webhook_signature varchar(16) not null default 'VALID';

alter table stripe_account
    alter column webhook_signature drop default;

alter table stripe_account
    add column webhook_timestamp_age text not null default 'PT0S';

alter table stripe_account
    alter column webhook_timestamp_age drop default;

alter table stripe_account
    add constraint stripe_account_webhook_secret_is_a_signing_secret
        check (webhook_secret is null or webhook_secret like 'whsec\_%');

alter table stripe_account
    add constraint stripe_account_webhook_signature_is_one_of_two
        check (webhook_signature in ('VALID', 'CORRUPTED'));

alter table stripe_account
    add constraint stripe_account_webhook_timestamp_age_is_a_duration
        check (webhook_timestamp_age ~ '^PT[0-9]');

alter table stripe_account
    add constraint stripe_account_signs_only_what_it_has_a_secret_for
        check (webhook_secret is not null
            or (webhook_signature = 'VALID' and webhook_timestamp_age = 'PT0S'));
