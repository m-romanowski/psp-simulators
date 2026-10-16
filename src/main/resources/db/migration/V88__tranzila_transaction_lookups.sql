create table tranzila_user_slot (
    test_run_id        varchar(64)  not null,
    account_id         varchar(128) not null,
    slot               smallint     not null,
    name               text         not null,
    first_recorded_at  timestamptz  not null,
    primary key (test_run_id, account_id, slot),
    unique (test_run_id, account_id, name),
    constraint tranzila_user_slot_is_one_of_the_twenty_five_published
        check (slot between 1 and 25)
);

insert into tranzila_user_slot (test_run_id, account_id, slot, name, first_recorded_at)
select test_run_id, account_id, 1, 'DCdisable', min(created_at)
from tranzila_transaction
where dc_disable is not null
group by test_run_id, account_id;

create table tranzila_transaction_user_field (
    test_run_id    varchar(64) not null,
    transaction_id bigint      not null,
    position       integer     not null,
    name           text        not null,
    value          text        not null,
    primary key (test_run_id, transaction_id, position),
    foreign key (test_run_id, transaction_id)
        references tranzila_transaction (test_run_id, transaction_id) on delete cascade,
    constraint tranzila_transaction_user_field_position_counts_from_zero
        check (position >= 0)
);

insert into tranzila_transaction_user_field (test_run_id, transaction_id, position, name, value)
select test_run_id, transaction_id, 0, 'DCdisable', dc_disable
from tranzila_transaction
where dc_disable is not null;

create table tranzila_transaction_item (
    item_id        bigint      generated always as identity,
    test_run_id    varchar(64) not null,
    transaction_id bigint      not null,
    position       integer     not null,
    name           text        not null,
    units_number   bigint      not null,
    unit_price     text        not null,
    item_type      text        not null,
    primary key (item_id),
    unique (test_run_id, transaction_id, position),
    foreign key (test_run_id, transaction_id)
        references tranzila_transaction (test_run_id, transaction_id) on delete cascade,
    constraint tranzila_transaction_item_position_counts_from_zero
        check (position >= 0),
    constraint tranzila_transaction_item_is_at_least_one_unit
        check (units_number >= 1),
    constraint tranzila_transaction_item_unit_price_is_a_decimal
        check (unit_price ~ '^[0-9]+(\.[0-9]+)?$')
);
