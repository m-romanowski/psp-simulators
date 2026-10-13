alter table stripe_account
    add column refund_status varchar(32) not null default 'SUCCEEDED';

alter table stripe_account
    alter column refund_status drop default;

alter table stripe_account
    add constraint stripe_account_refund_status_is_a_published_refund_status
        check (refund_status in ('PENDING', 'REQUIRES_ACTION', 'SUCCEEDED', 'FAILED', 'CANCELED'));
