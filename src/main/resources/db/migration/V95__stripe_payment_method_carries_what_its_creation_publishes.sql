alter table stripe_payment_method
    add column card_country                  varchar(2),
    add column card_display_brand            varchar(32),
    add column card_networks                 json,
    add column card_regulated_status         varchar(16),
    add column card_three_d_secure_supported boolean,
    add column billing_address_city          varchar(5000),
    add column billing_address_country       varchar(5000),
    add column billing_address_line1         varchar(5000),
    add column billing_address_line2         varchar(5000),
    add column billing_address_postal_code   varchar(5000),
    add column billing_address_state         varchar(5000),
    add column billing_email                 varchar(5000),
    add column billing_name                  varchar(5000),
    add column billing_phone                 varchar(5000),
    add column billing_tax_id                varchar(5000),
    add column metadata                      json        not null default '{}'::json,
    add column allow_redisplay               varchar(16) not null default 'unspecified',
    add column copied_from                   varchar(128);

update stripe_payment_method
set card_networks = json_build_array(card_brand)
where method_type = 'CARD';

alter table stripe_payment_method
    add constraint stripe_payment_method_card_facts_belong_to_a_card check (
        method_type = 'CARD'
        or (card_country is null
            and card_display_brand is null
            and card_networks is null
            and card_regulated_status is null
            and card_three_d_secure_supported is null)),
    add constraint stripe_payment_method_regulated_status
        check (card_regulated_status is null or card_regulated_status in ('regulated', 'unregulated')),
    add constraint stripe_payment_method_allow_redisplay
        check (allow_redisplay in ('always', 'limited', 'unspecified')),
    add constraint stripe_payment_method_metadata_is_an_object
        check (json_typeof(metadata) = 'object');
