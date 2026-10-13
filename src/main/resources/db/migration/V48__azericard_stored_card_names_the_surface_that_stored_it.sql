alter table azericard_stored_card
    add column origin varchar(32);

update azericard_stored_card
set origin = case when fixture_id is null then 'PROVIDER_API' else 'CONTROL_API' end
where origin is null;

alter table azericard_stored_card
    alter column origin set not null;

alter table azericard_stored_card
    add constraint azericard_stored_card_origin_is_a_surface_that_stores_one check (
        (stored_by_int_ref is not null and origin = 'PROVIDER_API')
        or (fixture_id is not null and origin in ('CONTROL_API', 'UI')));

create index azericard_stored_card_by_origin
    on azericard_stored_card (test_run_id, origin);
