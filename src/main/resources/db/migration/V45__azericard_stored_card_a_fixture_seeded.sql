alter table azericard_stored_card
    alter column stored_by_int_ref drop not null,
    alter column order_id drop not null;

alter table azericard_stored_card
    add column fixture_id varchar(64);

alter table azericard_stored_card
    add constraint azericard_stored_card_is_stored_by_a_payment_or_seeded check (
        (stored_by_int_ref is not null and order_id is not null and fixture_id is null)
        or (stored_by_int_ref is null and order_id is null and fixture_id is not null));

create index azericard_stored_card_by_fixture
    on azericard_stored_card (fixture_id);
