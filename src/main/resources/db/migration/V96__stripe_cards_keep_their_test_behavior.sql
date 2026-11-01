alter table stripe_payment_method add column card_test_behavior varchar(40);
alter table stripe_payment_method add constraint stripe_payment_method_test_behavior
    check (card_test_behavior is null or (method_type = 'CARD' and card_test_behavior in (
        'GENERIC_DECLINE', 'INSUFFICIENT_FUNDS', 'LOST_CARD', 'STOLEN_CARD', 'EXPIRED_CARD',
        'INCORRECT_CVC', 'PROCESSING_ERROR', 'VELOCITY_LIMIT', 'CHARGE_CUSTOMER_FAIL')));
alter table stripe_charge add column failure_code varchar(40);
update stripe_payment_method set card_test_behavior = case
    when copied_from in ('pm_card_visa_chargeDeclined', 'pm_card_chargeDeclined') then 'GENERIC_DECLINE'
    when copied_from in ('pm_card_visa_chargeDeclinedInsufficientFunds', 'pm_card_chargeDeclinedInsufficientFunds') then 'INSUFFICIENT_FUNDS'
    else null end
where method_type = 'CARD';
update stripe_charge charge set failure_code = intent.last_payment_error
from stripe_payment_intent intent
where charge.test_run_id = intent.test_run_id and charge.intent_id = intent.intent_id
    and charge.arrival = (select max(attempt.arrival) from stripe_charge attempt
        where attempt.test_run_id = charge.test_run_id and attempt.intent_id = charge.intent_id)
    and charge.status = 'failed';
alter table stripe_payment_intent drop constraint stripe_payment_intent_last_payment_error;
alter table stripe_payment_intent add constraint stripe_payment_intent_last_payment_error
    check (last_payment_error in ('generic_decline', 'insufficient_funds', 'lost_card', 'stolen_card',
        'card_velocity_exceeded', 'expired_card', 'incorrect_cvc', 'processing_error', 'authentication_required'));
alter table stripe_payment_intent drop constraint stripe_payment_intent_settles_with_a_published_decline;
alter table stripe_payment_intent add constraint stripe_payment_intent_settles_with_a_published_decline
    check (settles_with is null or settles_with in ('generic_decline', 'insufficient_funds', 'lost_card',
        'stolen_card', 'card_velocity_exceeded', 'expired_card', 'incorrect_cvc', 'processing_error', 'authentication_required'));
