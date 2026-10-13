alter table grow_account
    add column wire_form varchar(32) not null default 'LIVE_WIRE';

alter table grow_account
    alter column wire_form drop default;

alter table grow_account
    add constraint grow_account_wire_form
        check (wire_form in ('LIVE_WIRE', 'PUBLISHED_EXAMPLE'));
