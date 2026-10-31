alter table stripe_payment_intent
    add column automatic_payment_methods_enabled boolean default true,
    add column automatic_payment_methods_allow_redirects varchar(8),
    add column allowed_payment_method_types text[],
    add column excluded_payment_method_types text[],
    add column payment_method_configuration varchar(100),
    add column payment_method_types text[] not null default '{card}';

alter table stripe_payment_intent
    alter column automatic_payment_methods_enabled drop default,
    alter column payment_method_types drop default;

alter table stripe_payment_intent
    add constraint stripe_payment_intent_offers_a_type_this_simulator_models
        check (cardinality(payment_method_types) > 0
            and payment_method_types <@ array['card', 'paypal']::text[]),
    add constraint stripe_payment_intent_allow_redirects
        check (automatic_payment_methods_allow_redirects is null
            or (automatic_payment_methods_allow_redirects in ('always', 'never')
                and automatic_payment_methods_enabled is not null)),
    add constraint stripe_payment_intent_allowed_types_stand_alone
        check (allowed_payment_method_types is null
            or (automatic_payment_methods_enabled is null
                and excluded_payment_method_types is null
                and payment_method_configuration is null)),
    add constraint stripe_payment_intent_dynamic_settings_need_dynamic_selection
        check ((excluded_payment_method_types is null and payment_method_configuration is null)
            or coalesce(automatic_payment_methods_enabled, false));

alter table stripe_setup_intent
    add column automatic_payment_methods_enabled boolean default true,
    add column automatic_payment_methods_allow_redirects varchar(8),
    add column allowed_payment_method_types text[],
    add column excluded_payment_method_types text[],
    add column payment_method_configuration varchar(100),
    add column payment_method_types text[] not null default '{card,paypal}';

alter table stripe_setup_intent
    alter column automatic_payment_methods_enabled drop default,
    alter column payment_method_types drop default;

alter table stripe_setup_intent
    add constraint stripe_setup_intent_offers_a_type_this_simulator_models
        check (cardinality(payment_method_types) > 0
            and payment_method_types <@ array['card', 'paypal']::text[]),
    add constraint stripe_setup_intent_allow_redirects
        check (automatic_payment_methods_allow_redirects is null
            or (automatic_payment_methods_allow_redirects in ('always', 'never')
                and automatic_payment_methods_enabled is not null)),
    add constraint stripe_setup_intent_allowed_types_stand_alone
        check (allowed_payment_method_types is null
            or (automatic_payment_methods_enabled is null
                and excluded_payment_method_types is null
                and payment_method_configuration is null)),
    add constraint stripe_setup_intent_dynamic_settings_need_dynamic_selection
        check ((excluded_payment_method_types is null and payment_method_configuration is null)
            or coalesce(automatic_payment_methods_enabled, false));
