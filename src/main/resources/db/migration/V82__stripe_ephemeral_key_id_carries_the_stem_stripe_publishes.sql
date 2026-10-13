delete from stripe_customer where customer_id like 'ephkey/_sim/_%' escape '/';

delete from stripe_payment_method where payment_method_id like 'ephkey/_sim/_%' escape '/';

delete from stripe_setup_intent where setup_intent_id like 'ephkey/_sim/_%' escape '/';

delete from stripe_charge where charge_id like 'ephkey/_sim/_%' escape '/';

delete from stripe_ephemeral_key where ephemeral_key_id like 'ek/_sim/_%' escape '/';

alter table stripe_customer
    drop constraint stripe_customer_is_not_an_ephemeral_key;

alter table stripe_customer
    add constraint stripe_customer_is_not_an_ephemeral_key
        check (customer_id not like 'ephkey/_sim/_%' escape '/');

alter table stripe_payment_method
    drop constraint stripe_payment_method_is_not_an_ephemeral_key;

alter table stripe_payment_method
    add constraint stripe_payment_method_is_not_an_ephemeral_key
        check (payment_method_id not like 'ephkey/_sim/_%' escape '/');

alter table stripe_setup_intent
    drop constraint stripe_setup_intent_is_not_an_ephemeral_key;

alter table stripe_setup_intent
    add constraint stripe_setup_intent_is_not_an_ephemeral_key
        check (setup_intent_id not like 'ephkey/_sim/_%' escape '/');

alter table stripe_charge
    drop constraint stripe_charge_is_not_an_ephemeral_key;

alter table stripe_charge
    add constraint stripe_charge_is_not_an_ephemeral_key
        check (charge_id not like 'ephkey/_sim/_%' escape '/');

alter table stripe_ephemeral_key
    add constraint stripe_ephemeral_key_is_not_a_key_secret
        check (ephemeral_key_id not like 'ek/_sim/_%' escape '/');
