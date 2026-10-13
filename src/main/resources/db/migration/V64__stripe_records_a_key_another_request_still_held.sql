alter table stripe_idempotency_exchange
    drop constraint stripe_idempotency_exchange_verdict;

alter table stripe_idempotency_exchange
    add constraint stripe_idempotency_exchange_verdict
        check (verdict in ('FIRST_REQUEST', 'TRUE_IDEMPOTENT_REPLAY', 'CONFLICTING_PAYLOAD',
                           'REFUSED_WHILE_THE_KEY_WAS_HELD'));
