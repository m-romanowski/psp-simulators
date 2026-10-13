alter table stripe_payment_intent
    add column settles_at   timestamptz,
    add column settles_with varchar(32);

alter table stripe_payment_intent
    add constraint stripe_payment_intent_settles_with_a_published_decline
        check (settles_with is null or settles_with in (
            'generic_decline', 'insufficient_funds', 'expired_card',
            'incorrect_cvc', 'processing_error', 'authentication_required'));

alter table stripe_payment_intent
    add constraint stripe_payment_intent_only_a_processing_intent_is_owed_an_outcome
        check (settles_at is null or status = 'processing');

alter table stripe_payment_intent
    add constraint stripe_payment_intent_a_scheduled_decline_needs_a_scheduled_moment
        check (settles_with is null or settles_at is not null);
