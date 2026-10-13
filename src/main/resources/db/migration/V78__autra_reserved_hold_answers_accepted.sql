update autra_payment
set native_status = 'ACCEPTED'
where native_status = 'PRE_AUTHORIZED';

update autra_payment_operation
set native_status = 'ACCEPTED'
where native_status = 'PRE_AUTHORIZED';

update autra_payment_event
set status = 'ACCEPTED'
where status = 'PRE_AUTHORIZED';

alter table autra_order_claim
    drop constraint autra_order_claim_state,
    drop constraint autra_order_claim_names_the_payment_that_holds_it;

update autra_order_claim
set claim_state = 'HELD_BY_THE_PRE_AUTHORIZATION_IT_NAMES'
where claim_state = 'PRE_AUTHORIZED';

alter table autra_order_claim
    add constraint autra_order_claim_state
        check (claim_state in ('HELD_BEFORE_THE_ACQUIRER_WAS_CALLED',
                               'HELD_BY_THE_PRE_AUTHORIZATION_IT_NAMES')),
    add constraint autra_order_claim_names_the_payment_that_holds_it
        check ((claim_state = 'HELD_BEFORE_THE_ACQUIRER_WAS_CALLED' and payment_id is null)
               or (claim_state = 'HELD_BY_THE_PRE_AUTHORIZATION_IT_NAMES'
                   and payment_id is not null));

alter table autra_payment
    add constraint autra_payment_only_a_hold_can_have_been_released
        check (hold_released_at is null or payment_type = 'PREAUTH');
