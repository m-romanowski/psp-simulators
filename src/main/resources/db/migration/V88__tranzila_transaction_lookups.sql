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

create index tranzila_transaction_user_field_by_name
    on tranzila_transaction_user_field (test_run_id, name);

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
